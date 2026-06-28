package minioptimizer.optimizer.rules

import minioptimizer.ast.CompOp
import minioptimizer.expressions.*
import minioptimizer.optimizer.{PlanTransforms, PredicateUtils, Rule}
import minioptimizer.plans.logical.*

object RewritePredicateSubqueries extends Rule:
  override val name: String = "RewritePredicateSubqueries"

  override def apply(plan: LogicalPlan): LogicalPlan =
    PlanTransforms.transformUp(plan) {
      case Filter(condition, child) =>
        rewriteFilter(condition, child)
    }

  private def rewriteFilter(condition: LogicalExpression, child: LogicalPlan): LogicalPlan =
    val conjuncts = PredicateUtils.splitConjuncts(condition)

    val (subqueryPredicates, remainingPredicates) = conjuncts.partition {
      case _: InSubqueryExpression | _: ExistsExpression => true
      case _                                             => false
    }

    val withSemiJoins = subqueryPredicates.foldLeft(child) {
      case (current, InSubqueryExpression(value, subquery)) =>
        rewriteInSubquery(current, value, subquery)
      case (current, ExistsExpression(subquery)) =>
        rewriteExistsSubquery(current, subquery)
      case (current, _) =>
        current
    }

    PredicateUtils.combineConjuncts(remainingPredicates).map(Filter(_, withSemiJoins)).getOrElse(withSemiJoins)

  private def rewriteInSubquery(
      left: LogicalPlan,
      value: LogicalExpression,
      subquery: LogicalPlan
  ): LogicalPlan =
    subquery match
      case Project(projectList, subqueryChild) if projectList.size == 1 =>
        val (right, correlatedPredicates) = pullCorrelatedPredicates(subqueryChild, PredicateUtils.outputExprIds(left))
        val inCondition = ComparisonExpression(CompOp.Eq, value, projectList.head)
        val condition = PredicateUtils.combineConjuncts(inCondition +: correlatedPredicates)
        Join(left, right, JoinType.LeftSemi, condition)
      case _ =>
        Filter(InSubqueryExpression(value, subquery), left)

  private def rewriteExistsSubquery(left: LogicalPlan, subquery: LogicalPlan): LogicalPlan =
    subquery match
      case Project(_, subqueryChild) =>
        val (right, correlatedPredicates) = pullCorrelatedPredicates(subqueryChild, PredicateUtils.outputExprIds(left))
        val condition = PredicateUtils.combineConjuncts(correlatedPredicates)
        Join(left, right, JoinType.LeftSemi, condition)
      case _ =>
        Filter(ExistsExpression(subquery), left)

  private def pullCorrelatedPredicates(
      plan: LogicalPlan,
      outerIds: Set[Long]
  ): (LogicalPlan, Seq[LogicalExpression]) =
    plan match
      case Filter(condition, child) =>
        val (newChild, childCorrelated) = pullCorrelatedPredicates(child, outerIds)
        val conjuncts = PredicateUtils.splitConjuncts(condition)
        val (correlated, local) = conjuncts.partition(isCorrelated(_, outerIds))
        val rewrittenPlan = PredicateUtils.combineConjuncts(local).map(Filter(_, newChild)).getOrElse(newChild)
        rewrittenPlan -> (childCorrelated ++ correlated)

      case Project(projectList, child) =>
        val (newChild, correlated) = pullCorrelatedPredicates(child, outerIds)
        Project(projectList, newChild) -> correlated

      case Join(left, right, joinType, condition) =>
        val (newLeft, leftCorrelated) = pullCorrelatedPredicates(left, outerIds)
        val (newRight, rightCorrelated) = pullCorrelatedPredicates(right, outerIds)
        val conditionCorrelated = condition.toSeq.filter(isCorrelated(_, outerIds))
        val localCondition = condition.filter(expr => !isCorrelated(expr, outerIds))
        Join(newLeft, newRight, joinType, localCondition) -> (leftCorrelated ++ rightCorrelated ++ conditionCorrelated)

      case leaf =>
        leaf -> Seq.empty

  private def isCorrelated(predicate: LogicalExpression, outerIds: Set[Long]): Boolean =
    !PredicateUtils.containsSubquery(predicate) &&
      PredicateUtils.referencedExprIds(predicate).exists(outerIds.contains)
