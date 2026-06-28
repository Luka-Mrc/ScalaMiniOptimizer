package minioptimizer.optimizer.rules

import minioptimizer.expressions.*
import minioptimizer.optimizer.{PredicateUtils, Rule}
import minioptimizer.plans.logical.*

object PushDownProjections extends Rule:
  override val name: String = "PushDownProjections"

  override def apply(plan: LogicalPlan): LogicalPlan =
    prune(plan, PredicateUtils.outputExprIds(plan), trimOutput = false)

  private def prune(plan: LogicalPlan, requiredIds: Set[Long], trimOutput: Boolean): LogicalPlan =
    plan match
      case scan: Scan =>
        if trimOutput then maybeProject(requiredIds, scan) else scan

      case Project(projectList, child) =>
        val newProjectList = trimProjectList(projectList, requiredIds, trimOutput)
        val childRequiredIds = PredicateUtils.referencedExprIds(newProjectList)
        Project(newProjectList, prune(child, childRequiredIds, trimOutput = false))

      case Filter(condition, child) =>
        val childRequiredIds = requiredIds ++ PredicateUtils.referencedExprIds(condition)
        val newChild = prune(child, childRequiredIds, trimOutput = true)
        val newFilter = Filter(condition, newChild)
        if trimOutput then maybeProject(requiredIds, newFilter) else newFilter

      case Join(left, right, joinType, condition) =>
        val leftOutputIds = PredicateUtils.outputExprIds(left)
        val rightOutputIds = PredicateUtils.outputExprIds(right)
        val conditionIds = condition.map(PredicateUtils.referencedExprIds).getOrElse(Set.empty)

        val leftRequiredIds =
          (requiredIds intersect leftOutputIds) ++ (conditionIds intersect leftOutputIds)
        val rightRequiredIds =
          (requiredIds intersect rightOutputIds) ++ (conditionIds intersect rightOutputIds)

        val newJoin = Join(
          prune(left, leftRequiredIds, trimOutput = true),
          prune(right, rightRequiredIds, trimOutput = true),
          joinType,
          condition
        )
        if trimOutput then maybeProject(requiredIds, newJoin) else newJoin

      case other =>
        other

  private def maybeProject(requiredIds: Set[Long], plan: LogicalPlan): LogicalPlan =
    val outputIds = PredicateUtils.outputExprIds(plan)
    val neededIds = requiredIds intersect outputIds

    if neededIds.isEmpty || neededIds == outputIds then plan
    else
      val projectList = plan.output.filter(attr => neededIds.contains(attr.exprId))
      Project(projectList, plan)

  private def trimProjectList(
      projectList: Seq[LogicalExpression],
      requiredIds: Set[Long],
      trimOutput: Boolean
  ): Seq[LogicalExpression] =
    if !trimOutput || requiredIds.isEmpty then projectList
    else
      val trimmed = projectList.filter {
        case attr: AttributeReference => requiredIds.contains(attr.exprId)
        case _                        => true
      }
      if trimmed.isEmpty then projectList else trimmed
