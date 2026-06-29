package minioptimizer.cost

import minioptimizer.ast.{CompOp, Literal}
import minioptimizer.catalog.{Catalog, ColumnStatistics}
import minioptimizer.expressions.*
import minioptimizer.optimizer.PredicateUtils
import minioptimizer.plans.logical.*


final case class CostEstimate(
    rowCount: Double,
    cost: Double,
    attributes: Map[Long, ColumnStatistics]
)

final class CostEstimator(catalog: Catalog):

  private val DefaultScanRows = 1000.0
  private val DefaultDistinct = 100.0
  private val DefaultEqualitySelectivity = 0.1
  private val DefaultRangeSelectivity = 0.33
  private val DefaultSubquerySelectivity = 0.5

  def estimate(plan: LogicalPlan): CostEstimate =
    plan match
      case Scan(tableName, _, output) =>
        val tableStats = catalog.table(tableName).flatMap(_.statistics)
        val rows = tableStats.map(_.rowCount).getOrElse(DefaultScanRows)
        val attributes = output.map { attr =>
          val stats = tableStats
            .flatMap(_.forColumn(attr.name))
            .getOrElse(ColumnStatistics(distinctCount = Some(math.min(rows, DefaultDistinct))))
          attr.exprId -> stats
        }.toMap
        CostEstimate(rows, rows, attributes)

      case Filter(condition, child) =>
        val childEstimate = estimate(child)
        val selectivity = estimateSelectivity(condition, childEstimate)
        val rows = boundedRows(childEstimate.rowCount * selectivity, childEstimate.rowCount)
        val cost = childEstimate.cost + childEstimate.rowCount * 0.2
        CostEstimate(rows, cost, scaleAttributes(childEstimate.attributes, rows))

      case Project(projectList, child) =>
        val childEstimate = estimate(child)
        val projectedIds = PredicateUtils.referencedExprIds(projectList)
        val attributes =
          if projectedIds.isEmpty then Map.empty
          else childEstimate.attributes.filter { case (exprId, _) => projectedIds.contains(exprId) }
        val cost = childEstimate.cost + childEstimate.rowCount * 0.05
        CostEstimate(childEstimate.rowCount, cost, attributes)

      case Join(left, right, joinType, condition) =>
        val leftEstimate = estimate(left)
        val rightEstimate = estimate(right)
        val combined = combineAttributes(leftEstimate, rightEstimate)
        val selectivity = condition.map(estimateSelectivity(_, combined)).getOrElse(1.0)

        val outputRows = joinType match
          case JoinType.LeftSemi =>
            if condition.isEmpty && rightEstimate.rowCount > 0 then leftEstimate.rowCount
            else math.min(leftEstimate.rowCount, leftEstimate.rowCount * rightEstimate.rowCount * selectivity)
          case _ =>
            boundedRows(leftEstimate.rowCount * rightEstimate.rowCount * selectivity)

        val outputAttributes = joinType match
          case JoinType.LeftSemi => scaleAttributes(leftEstimate.attributes, outputRows)
          case _                 => scaleAttributes(combined.attributes, outputRows)

        val cost = leftEstimate.cost + rightEstimate.cost + leftEstimate.rowCount + rightEstimate.rowCount + outputRows
        CostEstimate(outputRows, cost, outputAttributes)

      case _: UnresolvedRelation =>
        CostEstimate(DefaultScanRows, DefaultScanRows, Map.empty)

  def annotatedTreeString(plan: LogicalPlan): String =
    def loop(current: LogicalPlan, indent: String): Seq[String] =
      val estimated = estimate(current)
      val line = s"$indent${current.simpleString} [rows=${format(estimated.rowCount)}, cost=${format(estimated.cost)}]"
      line +: current.children.flatMap(child => loop(child, indent + "  "))

    loop(plan, "").mkString("\n")

  private def combineAttributes(left: CostEstimate, right: CostEstimate): CostEstimate =
    CostEstimate(
      rowCount = left.rowCount * right.rowCount,
      cost = left.cost + right.cost,
      attributes = left.attributes ++ right.attributes
    )

  private def estimateSelectivity(expr: LogicalExpression, input: CostEstimate): Double =
    val selectivity = expr match
      case AndExpression(left, right) =>
        estimateSelectivity(left, input) * estimateSelectivity(right, input)

      case OrExpression(left, right) =>
        val leftSel = estimateSelectivity(left, input)
        val rightSel = estimateSelectivity(right, input)
        leftSel + rightSel - leftSel * rightSel

      case ComparisonExpression(op, left, right) =>
        estimateComparisonSelectivity(op, left, right, input)

      case _: InSubqueryExpression | _: ExistsExpression =>
        DefaultSubquerySelectivity

      case _ =>
        DefaultEqualitySelectivity

    clamp(selectivity, 0.0, 1.0)

  private def estimateComparisonSelectivity(
      op: CompOp,
      left: LogicalExpression,
      right: LogicalExpression,
      input: CostEstimate
  ): Double =
    (attributeStats(left, input), attributeStats(right, input), numericLiteral(left), numericLiteral(right)) match
      case (Some(leftStats), Some(rightStats), _, _) =>
        compareAttributes(op, leftStats, rightStats)

      case (Some(stats), None, _, Some(value)) =>
        compareAttributeWithLiteral(op, stats, value, input.rowCount)

      case (None, Some(stats), Some(value), _) =>
        compareAttributeWithLiteral(reverse(op), stats, value, input.rowCount)

      case _ =>
        defaultComparisonSelectivity(op)

  private def compareAttributes(op: CompOp, left: ColumnStatistics, right: ColumnStatistics): Double =
    op match
      case CompOp.Eq =>
        val leftNdv = left.distinctCount.getOrElse(DefaultDistinct)
        val rightNdv = right.distinctCount.getOrElse(DefaultDistinct)
        1.0 / math.max(1.0, math.max(leftNdv, rightNdv))
      case CompOp.Ne =>
        1.0 - compareAttributes(CompOp.Eq, left, right)
      case _ =>
        DefaultRangeSelectivity

  private def compareAttributeWithLiteral(
      op: CompOp,
      stats: ColumnStatistics,
      value: Double,
      inputRows: Double
  ): Double =
    op match
      case CompOp.Eq =>
        stats.histogram
          .map(histogram => histogram.equalityRows(value) / math.max(1.0, inputRows))
          .orElse(stats.distinctCount.map(ndv => 1.0 / math.max(1.0, ndv)))
          .getOrElse(DefaultEqualitySelectivity)

      case CompOp.Ne =>
        1.0 - compareAttributeWithLiteral(CompOp.Eq, stats, value, inputRows)

      case CompOp.Lt =>
        rangeSelectivity(stats, _ < value, inputRows)
      case CompOp.Le =>
        rangeSelectivity(stats, _ <= value, inputRows)
      case CompOp.Gt =>
        rangeSelectivity(stats, _ > value, inputRows)
      case CompOp.Ge =>
        rangeSelectivity(stats, _ >= value, inputRows)

  private def rangeSelectivity(stats: ColumnStatistics, keep: Double => Boolean, inputRows: Double): Double =
    stats.histogram
      .map(histogram => histogram.rangeRows(keep) / math.max(1.0, inputRows))
      .orElse(for
        min <- stats.min
        max <- stats.max
      yield
        if keep(min) && keep(max) then 1.0
        else if !keep(min) && !keep(max) then 0.0
        else DefaultRangeSelectivity)
      .getOrElse(DefaultRangeSelectivity)

  private def attributeStats(expr: LogicalExpression, input: CostEstimate): Option[ColumnStatistics] =
    expr match
      case attr: AttributeReference => input.attributes.get(attr.exprId)
      case _                        => None

  private def numericLiteral(expr: LogicalExpression): Option[Double] =
    expr match
      case LiteralExpression(Literal.IntLit(value))     => Some(value.toDouble)
      case LiteralExpression(Literal.DecimalLit(value)) => Some(value)
      case _                                            => None

  private def defaultComparisonSelectivity(op: CompOp): Double =
    op match
      case CompOp.Eq => DefaultEqualitySelectivity
      case CompOp.Ne => 1.0 - DefaultEqualitySelectivity
      case _         => DefaultRangeSelectivity

  private def reverse(op: CompOp): CompOp =
    op match
      case CompOp.Lt => CompOp.Gt
      case CompOp.Le => CompOp.Ge
      case CompOp.Gt => CompOp.Lt
      case CompOp.Ge => CompOp.Le
      case other     => other

  private def scaleAttributes(attributes: Map[Long, ColumnStatistics], rowCount: Double): Map[Long, ColumnStatistics] =
    attributes.view.mapValues { stats =>
      stats.copy(
        distinctCount = stats.distinctCount.map(ndv => math.min(ndv, rowCount)),
        nullCount = stats.nullCount.map(nulls => math.min(nulls, rowCount))
      )
    }.toMap

  private def boundedRows(value: Double, inputRows: Double = Double.PositiveInfinity): Double =
    if value <= 0.0 then 0.0
    else math.min(inputRows, math.max(1.0, value))

  private def clamp(value: Double, min: Double, max: Double): Double =
    math.max(min, math.min(max, value))

  private def format(value: Double): String =
    if value.isInfinity then "inf"
    else if value >= 1000000000.0 then f"${value / 1000000000.0}%.2fB"
    else if value >= 1000000.0 then f"${value / 1000000.0}%.2fM"
    else if value >= 1000.0 then f"${value / 1000.0}%.2fk"
    else if math.abs(value - value.round) < 0.01 then value.round.toString
    else f"$value%.2f"
