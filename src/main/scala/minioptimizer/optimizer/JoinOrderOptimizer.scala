package minioptimizer.optimizer

import minioptimizer.cost.CostEstimator
import minioptimizer.expressions.LogicalExpression
import minioptimizer.plans.logical.*


final class JoinOrderOptimizer(estimator: CostEstimator):

  def optimize(plan: LogicalPlan): LogicalPlan =
    plan match
      case Project(projectList, child) =>
        Project(projectList, optimize(child))

      case Filter(condition, child) =>
        Filter(condition, optimize(child))

      case Join(left, right, JoinType.LeftSemi, condition) =>
        Join(optimize(left), optimize(right), JoinType.LeftSemi, condition)

      case join @ Join(_, _, _, _) =>
        reorderJoinGroup(join)

      case leaf =>
        leaf

  private def reorderJoinGroup(plan: LogicalPlan): LogicalPlan =
    val group = collectJoinGroup(plan)

    if group.leaves.size <= 1 then group.leaves.headOption.getOrElse(plan)
    else
      val predicates = group.predicates.distinct
      val leafOutputs = group.leaves.map(PredicateUtils.outputExprIds)
      val exprToLeaf = leafOutputs.zipWithIndex.flatMap { case (exprIds, leafIndex) =>
        exprIds.map(_ -> leafIndex)
      }.toMap
      val predicateMasks = predicates.map(predicateLeafMask(_, exprToLeaf))
      val allLeavesMask = maskFor(group.leaves.indices)

      val bestByMask = scala.collection.mutable.Map.empty[Int, Candidate]

      group.leaves.zipWithIndex.foreach { case (leaf, index) =>
        val mask = 1 << index
        val estimate = estimator.estimate(leaf)
        bestByMask(mask) = Candidate(
          plan = leaf,
          leafMask = mask,
          rows = estimate.rowCount,
          cost = estimate.cost,
          crossJoins = 0,
          depth = 0
        )
      }

      for size <- 2 to group.leaves.size do
        subsetsOfSize(group.leaves.size, size).foreach { subset =>
          val best = splitMasks(subset)
            .flatMap { case (leftMask, rightMask) =>
              for
                left <- bestByMask.get(leftMask)
                right <- bestByMask.get(rightMask)
              yield buildCandidate(left, right, predicates, predicateMasks)
            }
            .reduceOption(bestCandidate)

          best.foreach(candidate => bestByMask(subset) = candidate)
        }

      val reordered = bestByMask.get(allLeavesMask).map(_.plan).getOrElse(plan)
      val residualPredicates = predicates.zip(predicateMasks).collect {
        case (predicate, mask) if Integer.bitCount(mask) <= 1 => predicate
      }
      PredicateUtils.combineConjuncts(residualPredicates).map(Filter(_, reordered)).getOrElse(reordered)

  private def collectJoinGroup(plan: LogicalPlan): JoinGroup =
    plan match
      case Join(left, right, joinType, condition) if reorderable(joinType) =>
        val leftGroup = collectJoinGroup(left)
        val rightGroup = collectJoinGroup(right)
        JoinGroup(
          leaves = leftGroup.leaves ++ rightGroup.leaves,
          predicates = leftGroup.predicates ++ rightGroup.predicates ++ condition.toSeq.flatMap(PredicateUtils.splitConjuncts)
        )

      case other =>
        JoinGroup(Seq(optimize(other)), Seq.empty)

  private def buildCandidate(
      left: Candidate,
      right: Candidate,
      predicates: Seq[LogicalExpression],
      predicateMasks: Seq[Int]
  ): Candidate =
    val combinedMask = left.leafMask | right.leafMask
    val boundaryPredicates = predicates.zip(predicateMasks).collect {
      case (predicate, mask)
          if mask != 0 &&
            (mask & combinedMask) == mask &&
            (mask & left.leafMask) != 0 &&
            (mask & right.leafMask) != 0 =>
        predicate
    }

    val joinType = if boundaryPredicates.nonEmpty then JoinType.Inner else JoinType.Cross
    val condition = PredicateUtils.combineConjuncts(boundaryPredicates)
    val plan = Join(left.plan, right.plan, joinType, condition)
    val estimate = estimator.estimate(plan)
    val crossPenalty = if boundaryPredicates.isEmpty then 1 else 0

    Candidate(
      plan = plan,
      leafMask = combinedMask,
      rows = estimate.rowCount,
      cost = estimate.cost,
      crossJoins = left.crossJoins + right.crossJoins + crossPenalty,
      depth = math.max(left.depth, right.depth) + 1
    )

  private def predicateLeafMask(predicate: LogicalExpression, exprToLeaf: Map[Long, Int]): Int =
    PredicateUtils.referencedExprIds(predicate).foldLeft(0) { (mask, exprId) =>
      exprToLeaf.get(exprId).map(index => mask | (1 << index)).getOrElse(mask)
    }

  private def bestCandidate(left: Candidate, right: Candidate): Candidate =
    if better(left, right) then left else right

  private def better(left: Candidate, right: Candidate): Boolean =
    if left.crossJoins != right.crossJoins then left.crossJoins < right.crossJoins
    else if math.abs(left.cost - right.cost) > 0.0001 then left.cost < right.cost
    else if math.abs(left.rows - right.rows) > 0.0001 then left.rows < right.rows
    else left.depth <= right.depth

  private def reorderable(joinType: JoinType): Boolean =
    joinType == JoinType.Inner || joinType == JoinType.Cross

  private def subsetsOfSize(leafCount: Int, size: Int): Seq[Int] =
    val maxMask = 1 << leafCount
    (1 until maxMask).filter(mask => Integer.bitCount(mask) == size)

  private def splitMasks(mask: Int): Seq[(Int, Int)] =
    val properSubsets = Iterator.iterate((mask - 1) & mask)(subset => (subset - 1) & mask)
      .takeWhile(_ != 0)
      .toSeq

    properSubsets.collect {
      case leftMask if leftMask < (mask ^ leftMask) => leftMask -> (mask ^ leftMask)
    }

  private def maskFor(indices: Iterable[Int]): Int =
    indices.foldLeft(0)((mask, index) => mask | (1 << index))

private final case class JoinGroup(
    leaves: Seq[LogicalPlan],
    predicates: Seq[LogicalExpression]
)

private final case class Candidate(
    plan: LogicalPlan,
    leafMask: Int,
    rows: Double,
    cost: Double,
    crossJoins: Int,
    depth: Int
)
