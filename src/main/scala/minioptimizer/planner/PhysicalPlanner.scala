package minioptimizer.planner

import minioptimizer.ast.CompOp
import minioptimizer.catalog.Catalog
import minioptimizer.cost.CostEstimator
import minioptimizer.expressions.*
import minioptimizer.optimizer.PredicateUtils
import minioptimizer.plans.logical.*
import minioptimizer.plans.physical.*


final class PhysicalPlanner(catalog: Catalog, estimator: CostEstimator):

  private val FilterCpuFactor = 0.2
  private val ProjectCpuFactor = 0.05
  private val IndexLookupFactor = 3.0
  private val IndexRowFactor = 1.5

  def plan(logical: LogicalPlan): PhysicalPlan =
    logical match
      case filter @ Filter(condition, scan: Scan) =>
        planFilteredScan(filter, condition, scan)

      case scan @ Scan(tableName, alias, output) =>
        planScan(scan)

      case filter @ Filter(condition, child) =>
        val physicalChild = plan(child)
        val estimate = estimator.estimate(filter)
        PhysicalFilter(
          condition = condition,
          child = physicalChild,
          estimatedRows = estimate.rowCount,
          estimatedCost = physicalChild.estimatedCost + physicalChild.estimatedRows * FilterCpuFactor
        )

      case project @ Project(projectList, child) =>
        val physicalChild = plan(child)
        val estimate = estimator.estimate(project)
        PhysicalProject(
          projectList = projectList,
          child = physicalChild,
          estimatedRows = estimate.rowCount,
          estimatedCost = physicalChild.estimatedCost + physicalChild.estimatedRows * ProjectCpuFactor
        )

      case join @ Join(left, right, joinType, condition) =>
        val physicalLeft = plan(left)
        val physicalRight = plan(right)
        val estimate = estimator.estimate(join)
        chooseJoin(
          left = physicalLeft,
          right = physicalRight,
          joinType = joinType,
          condition = condition,
          outputRows = estimate.rowCount
        )

      case relation: UnresolvedRelation =>
        throw new IllegalArgumentException(s"Cannot build a physical plan from unresolved relation: ${relation.simpleString}")

  private def planScan(scan: Scan): PhysicalScan =
    val estimate = estimator.estimate(scan)
    PhysicalScan(
      tableName = scan.tableName,
      alias = scan.alias,
      output = scan.output,
      estimatedRows = estimate.rowCount,
      estimatedCost = estimate.cost
    )

  private def planFilteredScan(filter: Filter, condition: LogicalExpression, scan: Scan): PhysicalPlan =
    val tableScan = planScan(scan)
    val filterEstimate = estimator.estimate(filter)
    val tableScanWithFilter = PhysicalFilter(
      condition = condition,
      child = tableScan,
      estimatedRows = filterEstimate.rowCount,
      estimatedCost = tableScan.estimatedCost + tableScan.estimatedRows * FilterCpuFactor
    )

    val indexScan = indexedAttribute(condition, scan).map { attr =>
      PhysicalIndexScan(
        tableName = scan.tableName,
        alias = scan.alias,
        indexColumn = attr.name,
        predicate = condition,
        output = scan.output,
        estimatedRows = filterEstimate.rowCount,
        estimatedCost = indexScanCost(tableScan.estimatedRows, filterEstimate.rowCount)
      )
    }

    indexScan.filter(_.estimatedCost < tableScanWithFilter.estimatedCost).getOrElse(tableScanWithFilter)

  private def chooseJoin(
      left: PhysicalPlan,
      right: PhysicalPlan,
      joinType: JoinType,
      condition: Option[LogicalExpression],
      outputRows: Double
  ): PhysicalPlan =
    val nestedLoop = PhysicalJoin(
      left = left,
      right = right,
      joinType = joinType,
      strategy = PhysicalJoinStrategy.NestedLoop,
      condition = condition,
      buildSide = None,
      estimatedRows = outputRows,
      estimatedCost = nestedLoopCost(left, right, outputRows)
    )

    val hashJoin = for
      cond <- condition
      if canUseHashJoin(joinType, cond, left, right)
    yield
      PhysicalJoin(
        left = left,
        right = right,
        joinType = joinType,
        strategy = PhysicalJoinStrategy.Hash,
        condition = condition,
        buildSide = Some(chooseBuildSide(joinType, left, right)),
        estimatedRows = outputRows,
        estimatedCost = hashJoinCost(left, right, outputRows)
      )

    hashJoin.filter(_.estimatedCost < nestedLoop.estimatedCost).getOrElse(nestedLoop)

  private def canUseHashJoin(
      joinType: JoinType,
      condition: LogicalExpression,
      left: PhysicalPlan,
      right: PhysicalPlan
  ): Boolean =
    (joinType == JoinType.Inner || joinType == JoinType.LeftSemi) &&
      PredicateUtils.splitConjuncts(condition).exists(predicate => isHashableEquality(predicate, left, right))

  private def isHashableEquality(
      predicate: LogicalExpression,
      left: PhysicalPlan,
      right: PhysicalPlan
  ): Boolean =
    val leftIds = left.output.map(_.exprId).toSet
    val rightIds = right.output.map(_.exprId).toSet

    predicate match
      case ComparisonExpression(CompOp.Eq, leftAttr: AttributeReference, rightAttr: AttributeReference) =>
        (leftIds.contains(leftAttr.exprId) && rightIds.contains(rightAttr.exprId)) ||
          (leftIds.contains(rightAttr.exprId) && rightIds.contains(leftAttr.exprId))
      case _ =>
        false

  private def chooseBuildSide(joinType: JoinType, left: PhysicalPlan, right: PhysicalPlan): BuildSide =
    joinType match
      case JoinType.LeftSemi => BuildSide.Right
      case _ =>
        if left.estimatedRows <= right.estimatedRows then BuildSide.Left else BuildSide.Right

  private def nestedLoopCost(left: PhysicalPlan, right: PhysicalPlan, outputRows: Double): Double =
    left.estimatedCost + right.estimatedCost + left.estimatedRows * right.estimatedRows + outputRows

  private def hashJoinCost(left: PhysicalPlan, right: PhysicalPlan, outputRows: Double): Double =
    left.estimatedCost + right.estimatedCost + left.estimatedRows + right.estimatedRows + outputRows

  private def indexedAttribute(condition: LogicalExpression, scan: Scan): Option[AttributeReference] =
    val scanIds = scan.output.map(_.exprId).toSet
    val table = catalog.table(scan.tableName)

    PredicateUtils.splitConjuncts(condition).flatMap(indexedComparisonAttribute).find { attr =>
      scanIds.contains(attr.exprId) && table.exists(_.isIndexedColumn(attr.name))
    }

  private def indexedComparisonAttribute(predicate: LogicalExpression): Option[AttributeReference] =
    predicate match
      case ComparisonExpression(_, attr: AttributeReference, _: LiteralExpression) =>
        Some(attr)
      case ComparisonExpression(_, _: LiteralExpression, attr: AttributeReference) =>
        Some(attr)
      case _ =>
        None

  private def indexScanCost(inputRows: Double, outputRows: Double): Double =
    val lookupCost = math.log(math.max(1.0, inputRows)) / math.log(2.0) * IndexLookupFactor
    lookupCost + outputRows * IndexRowFactor
