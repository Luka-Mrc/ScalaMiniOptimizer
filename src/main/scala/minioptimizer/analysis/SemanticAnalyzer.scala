package minioptimizer.analysis

import minioptimizer.ast.*
import minioptimizer.catalog.{Catalog, Table}
import minioptimizer.expressions.*
import minioptimizer.plans.logical.*
import scala.collection.mutable.ListBuffer

/** A correlated reference: a column inside a subquery that binds to an outer query. */
final case class CorrelatedRef(column: String, qualifier: String, depth: Int)

/** Result of a successful analysis: parser AST, unresolved plan, resolved plan, correlations. */
final case class AnalysisReport(
    statement: SelectStatement,
    unresolved: LogicalPlan,
    resolved: LogicalPlan,
    correlations: Seq[CorrelatedRef]
)

final class SemanticAnalyzer(catalog: Catalog):

  private final case class ResolvedAttr(attribute: AttributeReference, depth: Int)

  def analyze(stmt: SelectStatement): Either[AnalysisError, AnalysisReport] =
    val unresolved = LogicalPlanBuilder.fromAst(stmt)
    val correlations = ListBuffer.empty[CorrelatedRef]
    resolvePlan(unresolved, Nil, correlations).map { resolved =>
      AnalysisReport(stmt, unresolved, resolved, correlations.toList)
    }

  private def resolvePlan(
      plan: LogicalPlan,
      outer: List[Seq[AttributeReference]],
      corr: ListBuffer[CorrelatedRef]
  ): Either[AnalysisError, LogicalPlan] =
    plan match
      case UnresolvedRelation(name, alias) =>
        catalog.table(name)
          .toRight(AnalysisError.TableNotFound(name))
          .map(table => scanFor(table, alias))

      case scan: Scan =>
        Right(scan)

      case Join(left, right, joinType, condition) =>
        for
          resolvedLeft <- resolvePlan(left, outer, corr)
          resolvedRight <- resolvePlan(right, outer, corr)
          currentOutput = resolvedLeft.output ++ resolvedRight.output
          resolvedCondition <- condition match
            case Some(expr) => resolveExpression(expr, currentOutput, outer, corr).map(r => Some(r._1))
            case None       => Right(None)
        yield Join(resolvedLeft, resolvedRight, joinType, resolvedCondition)

      case Filter(condition, child) =>
        for
          resolvedChild <- resolvePlan(child, outer, corr)
          resolvedCondition <- resolveExpression(condition, resolvedChild.output, outer, corr).map(_._1)
        yield Filter(resolvedCondition, resolvedChild)

      case Project(projectList, child) =>
        for
          resolvedChild <- resolvePlan(child, outer, corr)
          currentOutput = resolvedChild.output
          resolvedProjectList <- traverse(projectList)(expr => resolveExpression(expr, currentOutput, outer, corr).map(_._1))
        yield Project(resolvedProjectList, resolvedChild)

  private def scanFor(table: Table, alias: Option[String]): Scan =
    val qualifier = alias.getOrElse(table.name)
    val output = table.columns.map { column =>
      AttributeReference(qualifier, column.name, Some(column.dataType))
    }
    Scan(table.name, alias, output)

  private def resolveSubquery(
      subquery: LogicalPlan,
      currentOutput: Seq[AttributeReference],
      outer: List[Seq[AttributeReference]],
      corr: ListBuffer[CorrelatedRef]
  ): Either[AnalysisError, LogicalPlan] =
    resolvePlan(subquery, currentOutput :: outer, corr)

  private def resolveExpression(
      expr: LogicalExpression,
      currentOutput: Seq[AttributeReference],
      outer: List[Seq[AttributeReference]],
      corr: ListBuffer[CorrelatedRef]
  ): Either[AnalysisError, (LogicalExpression, Option[DataType])] =
    expr match
      case LiteralExpression(value) =>
        Right(expr -> value.dataType)

      case UnresolvedAttribute(table, name) =>
        resolveAttribute(table, name, currentOutput, outer, corr).map { attr =>
          attr -> attr.dataType
        }

      case attr: AttributeReference =>
        Right(attr -> attr.dataType)

      case ArithmeticExpression(op, left, right, _) =>
        for
          (resolvedLeft, leftType) <- resolveExpression(left, currentOutput, outer, corr)
          (resolvedRight, rightType) <- resolveExpression(right, currentOutput, outer, corr)
          resultType <- arithResult(leftType, rightType)
        yield ArithmeticExpression(op, resolvedLeft, resolvedRight, resultType) -> resultType

      case ComparisonExpression(op, left, right) =>
        for
          (resolvedLeft, leftType) <- resolveExpression(left, currentOutput, outer, corr)
          (resolvedRight, rightType) <- resolveExpression(right, currentOutput, outer, corr)
          _ <- compatible("predikatu", leftType, rightType)
        yield ComparisonExpression(op, resolvedLeft, resolvedRight) -> None

      case AndExpression(left, right) =>
        for
          (resolvedLeft, _) <- resolveExpression(left, currentOutput, outer, corr)
          (resolvedRight, _) <- resolveExpression(right, currentOutput, outer, corr)
        yield AndExpression(resolvedLeft, resolvedRight) -> None

      case OrExpression(left, right) =>
        for
          (resolvedLeft, _) <- resolveExpression(left, currentOutput, outer, corr)
          (resolvedRight, _) <- resolveExpression(right, currentOutput, outer, corr)
        yield OrExpression(resolvedLeft, resolvedRight) -> None

      case InSubqueryExpression(value, subquery) =>
        for
          (resolvedValue, valueType) <- resolveExpression(value, currentOutput, outer, corr)
          resolvedSubquery <- resolveSubquery(subquery, currentOutput, outer, corr)
          subqueryType <- scalarSubqueryType(resolvedSubquery)
          _ <- compatible("IN podupitu", valueType, subqueryType)
        yield InSubqueryExpression(resolvedValue, resolvedSubquery) -> None

      case ExistsExpression(subquery) =>
        resolveSubquery(subquery, currentOutput, outer, corr)
          .map(resolvedSubquery => ExistsExpression(resolvedSubquery) -> None)

  private def scalarSubqueryType(plan: LogicalPlan): Either[AnalysisError, Option[DataType]] =
    plan match
      case Project(projectList, _) =>
        Either.cond(
          projectList.size == 1,
          projectList.head.dataType,
          AnalysisError.SubqueryMustReturnOneColumn(projectList.size)
        )
      case _ =>
        Left(AnalysisError.SubqueryMustReturnOneColumn(plan.output.size))

  private def resolveAttribute(
      table: Option[String],
      name: String,
      currentOutput: Seq[AttributeReference],
      outer: List[Seq[AttributeReference]],
      corr: ListBuffer[CorrelatedRef]
  ): Either[AnalysisError, AttributeReference] =
    resolveAttributeRaw(table, name, currentOutput, outer).map { resolved =>
      if resolved.depth > 0 then
        corr += CorrelatedRef(name, resolved.attribute.qualifier, resolved.depth)
      resolved.attribute
    }

  private def resolveAttributeRaw(
      table: Option[String],
      name: String,
      currentOutput: Seq[AttributeReference],
      outer: List[Seq[AttributeReference]]
  ): Either[AnalysisError, ResolvedAttr] =
    val scopes = currentOutput :: outer
    table match
      case Some(qualifier) =>
        scopes.zipWithIndex.collectFirst {
          case (scope, depth) if scope.exists(_.qualifier == qualifier) => scope -> depth
        } match
          case None =>
            Left(AnalysisError.UnknownTableReference(qualifier))
          case Some((scope, depth)) =>
            scope.find(attr => attr.qualifier == qualifier && attr.name == name) match
              case Some(attr) => Right(ResolvedAttr(attr, depth))
              case None       => Left(AnalysisError.ColumnNotFound(s"$qualifier.$name"))

      case None =>
        val hits = scopes.zipWithIndex.flatMap { case (scope, depth) =>
          scope.filter(_.name == name).map(attr => ResolvedAttr(attr, depth))
        }
        hits match
          case Nil => Left(AnalysisError.ColumnNotFound(name))
          case _ =>
            val minDepth = hits.map(_.depth).min
            hits.filter(_.depth == minDepth) match
              case single :: Nil => Right(single)
              case many          => Left(AnalysisError.AmbiguousColumn(name, many.map(_.attribute.qualifier)))

  private def arithResult(
      leftType: Option[DataType],
      rightType: Option[DataType]
  ): Either[AnalysisError, Option[DataType]] =
    def requireNumeric(tpe: Option[DataType]): Either[AnalysisError, Unit] = tpe match
      case Some(t) if !DataType.isNumeric(t) => Left(AnalysisError.NonNumericArithmetic(t))
      case _                                 => Right(())

    for
      _ <- requireNumeric(leftType)
      _ <- requireNumeric(rightType)
    yield (leftType, rightType) match
      case (Some(DataType.DecimalType), _) | (_, Some(DataType.DecimalType)) => Some(DataType.DecimalType)
      case (Some(DataType.IntType), _) | (_, Some(DataType.IntType))         => Some(DataType.IntType)
      case _                                                                 => None

  private def compatible(
      context: String,
      leftType: Option[DataType],
      rightType: Option[DataType]
  ): Either[AnalysisError, Unit] =
    (leftType, rightType) match
      case (None, _) | (_, None) => Right(())
      case (Some(left), Some(right)) =>
        if left == right || (DataType.isNumeric(left) && DataType.isNumeric(right)) then Right(())
        else Left(AnalysisError.TypeMismatch(context, left, right))

  private def traverse[A, B](xs: Seq[A])(f: A => Either[AnalysisError, B]): Either[AnalysisError, Seq[B]] =
    xs.foldRight[Either[AnalysisError, List[B]]](Right(Nil)) { (a, acc) =>
      for
        b <- f(a)
        bs <- acc
      yield b :: bs
    }
