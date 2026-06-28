package minioptimizer.optimizer

import minioptimizer.optimizer.rules.{
  ExtractJoinPredicates,
  PushDownFilters,
  PushDownProjections,
  RewritePredicateSubqueries
}
import minioptimizer.plans.logical.LogicalPlan

final class RuleBasedOptimizer:

  private val executor = RuleExecutor(
    Seq(
      Batch(
        name = "Predicate rewrite rules",
        maxIterations = 10,
        rules = Seq(RewritePredicateSubqueries, PushDownFilters, ExtractJoinPredicates)
      ),
      Batch(
        name = "Projection pushdown rules",
        maxIterations = 10,
        rules = Seq(PushDownProjections)
      )
    )
  )

  def optimize(plan: LogicalPlan): LogicalPlan =
    executor.execute(plan)
