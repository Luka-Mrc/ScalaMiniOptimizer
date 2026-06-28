package minioptimizer.optimizer.rules

import minioptimizer.expressions.LogicalExpression
import minioptimizer.optimizer.{PlanTransforms, PredicateUtils, Rule}
import minioptimizer.plans.logical.*

object PushDownFilters extends Rule:
  override val name: String = "PushDownFilters"

  override def apply(plan: LogicalPlan): LogicalPlan =
    PlanTransforms.transformUp(plan) {
      case Filter(condition, join @ Join(left, right, joinType, joinCondition)) =>
        val conjuncts = PredicateUtils.splitConjuncts(condition)
        val leftIds = PredicateUtils.outputExprIds(left)
        val rightIds = PredicateUtils.outputExprIds(right)

        val leftPredicates = conjuncts.filter(canPushTo(_, leftIds))
        val rightPredicates = conjuncts.filter(canPushTo(_, rightIds))
        val pushed = leftPredicates.toSet ++ rightPredicates.toSet
        val remaining = conjuncts.filterNot(pushed.contains)

        if leftPredicates.isEmpty && rightPredicates.isEmpty then Filter(condition, join)
        else
          val newLeft = withFilter(leftPredicates, left)
          val newRight = withFilter(rightPredicates, right)
          val newJoin = Join(newLeft, newRight, joinType, joinCondition)
          PredicateUtils.combineConjuncts(remaining).map(Filter(_, newJoin)).getOrElse(newJoin)
    }

  private def canPushTo(predicate: LogicalExpression, targetIds: Set[Long]): Boolean =
    val refs = PredicateUtils.referencedExprIds(predicate)
    refs.nonEmpty &&
      !PredicateUtils.containsSubquery(predicate) &&
      refs.subsetOf(targetIds)

  private def withFilter(predicates: Seq[LogicalExpression], child: LogicalPlan): LogicalPlan =
    PredicateUtils.combineConjuncts(predicates).map(Filter(_, child)).getOrElse(child)
