package minioptimizer.optimizer

import minioptimizer.expressions.*
import minioptimizer.plans.logical.LogicalPlan

object PredicateUtils:

  def splitConjuncts(expr: LogicalExpression): Seq[LogicalExpression] =
    expr match
      case AndExpression(left, right) => splitConjuncts(left) ++ splitConjuncts(right)
      case other                      => Seq(other)

  def combineConjuncts(exprs: Seq[LogicalExpression]): Option[LogicalExpression] =
    exprs.reduceLeftOption(AndExpression.apply)

  def referencedExprIds(expr: LogicalExpression): Set[Long] =
    referencedAttributes(expr).map(_.exprId)

  def outputExprIds(plan: LogicalPlan): Set[Long] =
    plan.output.map(_.exprId).toSet

  def containsSubquery(expr: LogicalExpression): Boolean =
    expr match
      case _: InSubqueryExpression | _: ExistsExpression => true
      case other                                         => other.children.exists(containsSubquery)

  private def referencedAttributes(expr: LogicalExpression): Set[AttributeReference] =
    expr match
      case attr: AttributeReference => Set(attr)
      case other                    => other.children.flatMap(referencedAttributes).toSet
