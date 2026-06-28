package minioptimizer.optimizer

import minioptimizer.plans.logical.LogicalPlan

final class RuleExecutor(batches: Seq[Batch]):

  def execute(plan: LogicalPlan): LogicalPlan =
    batches.foldLeft(plan) { (currentPlan, batch) =>
      executeBatch(currentPlan, batch)
    }

  private def executeBatch(plan: LogicalPlan, batch: Batch): LogicalPlan =
    var current = plan
    var iteration = 0
    var changed = true

    while changed && iteration < batch.maxIterations do
      val next = batch.rules.foldLeft(current) { (p, rule) => rule(p) }
      changed = next != current
      current = next
      iteration += 1

    current
