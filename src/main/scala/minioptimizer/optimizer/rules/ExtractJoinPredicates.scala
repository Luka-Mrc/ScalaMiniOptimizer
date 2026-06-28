package minioptimizer.optimizer.rules

import minioptimizer.expressions.{ComparisonExpression, LogicalExpression}
import minioptimizer.optimizer.{PlanTransforms, PredicateUtils, Rule}
import minioptimizer.plans.logical.*

object ExtractJoinPredicates extends Rule:
  override val name: String = "ExtractJoinPredicates"

  override def apply(plan: LogicalPlan): LogicalPlan =
    PlanTransforms.transformUp(plan) {
      case Filter(condition, join @ Join(left, right, joinType, joinCondition)) =>
        val conjuncts = PredicateUtils.splitConjuncts(condition)
        val leftIds = PredicateUtils.outputExprIds(left)
        val rightIds = PredicateUtils.outputExprIds(right)

        val joinPredicates = conjuncts.filter(isJoinPredicate(_, leftIds, rightIds))
        val remaining = conjuncts.filterNot(joinPredicates.toSet.contains)

        if joinPredicates.isEmpty then Filter(condition, join)
        else
          val combinedCondition =
            PredicateUtils.combineConjuncts(joinCondition.toSeq ++ joinPredicates)
          val newJoinType =
            if joinType == JoinType.Cross then JoinType.Inner else joinType
          val newJoin = Join(left, right, newJoinType, combinedCondition)
          PredicateUtils.combineConjuncts(remaining).map(Filter(_, newJoin)).getOrElse(newJoin)
    }

  private def isJoinPredicate(
      predicate: LogicalExpression,
      leftIds: Set[Long],
      rightIds: Set[Long]
  ): Boolean =
    predicate match
      case _: ComparisonExpression =>
        val refs = PredicateUtils.referencedExprIds(predicate)
        refs.nonEmpty &&
          !PredicateUtils.containsSubquery(predicate) &&
          refs.subsetOf(leftIds ++ rightIds) &&
          refs.exists(leftIds.contains) &&
          refs.exists(rightIds.contains)
      case _ => false
