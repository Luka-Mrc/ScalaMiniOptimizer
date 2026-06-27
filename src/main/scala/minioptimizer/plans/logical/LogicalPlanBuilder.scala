package minioptimizer.plans.logical

import minioptimizer.ast.{BoolExpr, Expression, Predicate, Relation, SelectStatement}
import minioptimizer.expressions.*

object LogicalPlanBuilder:

  def fromAst(stmt: SelectStatement): LogicalPlan =
    val fromPlan = buildFrom(stmt.from)
    val filtered = stmt.where match
      case Some(where) => Filter(buildBool(where), fromPlan)
      case None        => fromPlan
    Project(stmt.projections.map(buildExpression), filtered)

  private def buildFrom(relations: Seq[Relation]): LogicalPlan =
    relations
      .map[LogicalPlan](rel => UnresolvedRelation(rel.name, rel.alias))
      .reduceLeft((left, right) => Join(left, right, JoinType.Cross, None))

  private def buildBool(expr: BoolExpr): LogicalExpression = expr match
    case BoolExpr.And(left, right) => AndExpression(buildBool(left), buildBool(right))
    case BoolExpr.Or(left, right)  => OrExpression(buildBool(left), buildBool(right))
    case BoolExpr.Pred(predicate)  => buildPredicate(predicate)

  private def buildPredicate(predicate: Predicate): LogicalExpression = predicate match
    case Predicate.Comparison(op, left, right) =>
      ComparisonExpression(op, buildExpression(left), buildExpression(right))
    case Predicate.InSubquery(expr, subquery) =>
      InSubqueryExpression(buildExpression(expr), fromAst(subquery))
    case Predicate.Exists(subquery) =>
      ExistsExpression(fromAst(subquery))

  private def buildExpression(expr: Expression): LogicalExpression = expr match
    case Expression.Column(table, name) =>
      UnresolvedAttribute(table, name)
    case Expression.Lit(value) =>
      LiteralExpression(value)
    case Expression.BinaryArith(op, left, right) =>
      ArithmeticExpression(op, buildExpression(left), buildExpression(right))
