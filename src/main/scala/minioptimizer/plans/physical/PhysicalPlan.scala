package minioptimizer.plans.physical

import minioptimizer.expressions.{AttributeReference, LogicalExpression}
import minioptimizer.plans.logical.JoinType


sealed trait PhysicalPlan:
  def children: Seq[PhysicalPlan] = Nil
  def output: Seq[AttributeReference]
  def estimatedRows: Double
  def estimatedCost: Double
  def simpleString: String

  final def treeString: String =
    def loop(plan: PhysicalPlan, indent: String): Seq[String] =
      val current = indent + plan.simpleString
      current +: plan.children.flatMap(child => loop(child, indent + "  "))
    loop(this, "").mkString("\n")

  protected final def estimateString: String =
    s"rows=${PhysicalPlan.format(estimatedRows)}, cost=${PhysicalPlan.format(estimatedCost)}"

object PhysicalPlan:
  def format(value: Double): String =
    if value.isInfinity then "inf"
    else if value >= 1000000000.0 then f"${value / 1000000000.0}%.2fB"
    else if value >= 1000000.0 then f"${value / 1000000.0}%.2fM"
    else if value >= 1000.0 then f"${value / 1000.0}%.2fk"
    else if math.abs(value - value.round) < 0.01 then value.round.toString
    else f"$value%.2f"

enum PhysicalJoinStrategy:
  case NestedLoop
  case Hash

enum BuildSide:
  case Left
  case Right

final case class PhysicalScan(
    tableName: String,
    alias: Option[String],
    override val output: Seq[AttributeReference],
    override val estimatedRows: Double,
    override val estimatedCost: Double
) extends PhysicalPlan:
  override def simpleString: String =
    val name = alias.map(a => s"TableScan($tableName AS $a)").getOrElse(s"TableScan($tableName)")
    s"$name [$estimateString]"

final case class PhysicalIndexScan(
    tableName: String,
    alias: Option[String],
    indexColumn: String,
    predicate: LogicalExpression,
    override val output: Seq[AttributeReference],
    override val estimatedRows: Double,
    override val estimatedCost: Double
) extends PhysicalPlan:
  override def simpleString: String =
    val name = alias.map(a => s"IndexScan($tableName AS $a)").getOrElse(s"IndexScan($tableName)")
    s"$name(index=$indexColumn, predicate=${predicate.simpleString}) [$estimateString]"

final case class PhysicalFilter(
    condition: LogicalExpression,
    child: PhysicalPlan,
    override val estimatedRows: Double,
    override val estimatedCost: Double
) extends PhysicalPlan:
  override def children: Seq[PhysicalPlan] = Seq(child)
  override def output: Seq[AttributeReference] = child.output
  override def simpleString: String =
    s"Filter(${condition.simpleString}) [$estimateString]"

final case class PhysicalProject(
    projectList: Seq[LogicalExpression],
    child: PhysicalPlan,
    override val estimatedRows: Double,
    override val estimatedCost: Double
) extends PhysicalPlan:
  override def children: Seq[PhysicalPlan] = Seq(child)
  override def output: Seq[AttributeReference] =
    projectList.collect { case attr: AttributeReference => attr }
  override def simpleString: String =
    s"Project(${projectList.map(_.simpleString).mkString(", ")}) [$estimateString]"

final case class PhysicalJoin(
    left: PhysicalPlan,
    right: PhysicalPlan,
    joinType: JoinType,
    strategy: PhysicalJoinStrategy,
    condition: Option[LogicalExpression],
    buildSide: Option[BuildSide],
    override val estimatedRows: Double,
    override val estimatedCost: Double
) extends PhysicalPlan:
  override def children: Seq[PhysicalPlan] = Seq(left, right)
  override def output: Seq[AttributeReference] =
    joinType match
      case JoinType.LeftSemi => left.output
      case _                 => left.output ++ right.output
  override def simpleString: String =
    val conditionText = condition.map(c => s"(${c.simpleString})").getOrElse("")
    val buildText = buildSide.map(side => s", build=$side").getOrElse("")
    s"Join[$joinType, $strategy$buildText]$conditionText [$estimateString]"
