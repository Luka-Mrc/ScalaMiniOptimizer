package minioptimizer.optimizer

import minioptimizer.plans.logical.*

object PlanTransforms:

  def transformUp(plan: LogicalPlan)(rule: PartialFunction[LogicalPlan, LogicalPlan]): LogicalPlan =
    val withTransformedChildren = mapChildren(plan)(child => transformUp(child)(rule))
    if rule.isDefinedAt(withTransformedChildren) then rule(withTransformedChildren)
    else withTransformedChildren

  private def mapChildren(plan: LogicalPlan)(f: LogicalPlan => LogicalPlan): LogicalPlan =
    plan match
      case Join(left, right, joinType, condition) =>
        Join(f(left), f(right), joinType, condition)
      case Filter(condition, child) =>
        Filter(condition, f(child))
      case Project(projectList, child) =>
        Project(projectList, f(child))
      case leaf =>
        leaf
