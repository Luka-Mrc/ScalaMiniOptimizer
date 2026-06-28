package minioptimizer.optimizer

import minioptimizer.plans.logical.LogicalPlan

trait Rule:
  def name: String
  def apply(plan: LogicalPlan): LogicalPlan

final case class Batch(name: String, maxIterations: Int, rules: Seq[Rule])
