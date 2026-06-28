package minioptimizer.optimizer

import minioptimizer.optimizer.rules.{ExtractJoinPredicates, PushDownFilters}
import minioptimizer.plans.logical.LogicalPlan

final class RuleBasedOptimizer:

  private val executor = RuleExecutor(
    Seq(
      Batch(
        name = "Classic logical rules",
        maxIterations = 10,
        rules = Seq(PushDownFilters, ExtractJoinPredicates)
      )
    )
  )

  def optimize(plan: LogicalPlan): LogicalPlan =
    executor.execute(plan)
