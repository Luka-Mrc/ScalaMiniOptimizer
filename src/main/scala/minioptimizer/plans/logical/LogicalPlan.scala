package minioptimizer.plans.logical

import minioptimizer.expressions.{AttributeReference, LogicalExpression}

sealed trait LogicalPlan:
  def children: Seq[LogicalPlan] = Nil
  def expressions: Seq[LogicalExpression] = Nil
  def output: Seq[AttributeReference]
  def resolved: Boolean = children.forall(_.resolved) && expressions.forall(_.resolved)
  def simpleString: String = getClass.getSimpleName.stripSuffix("$")

  final def treeString: String =
    def loop(plan: LogicalPlan, indent: String): Seq[String] =
      val current = indent + plan.simpleString
      current +: plan.children.flatMap(child => loop(child, indent + "  "))
    loop(this, "").mkString("\n")

final case class UnresolvedRelation(name: String, alias: Option[String]) extends LogicalPlan:
  override def output: Seq[AttributeReference] = Nil
  override def resolved: Boolean = false
  def qualifier: String = alias.getOrElse(name)
  override def simpleString: String =
    alias.map(a => s"UnresolvedRelation($name AS $a)").getOrElse(s"UnresolvedRelation($name)")

final case class Scan(tableName: String, alias: Option[String], output: Seq[AttributeReference]) extends LogicalPlan:
  def qualifier: String = alias.getOrElse(tableName)
  override def simpleString: String =
    alias.map(a => s"Scan($tableName AS $a)").getOrElse(s"Scan($tableName)")

enum JoinType:
  case Cross

final case class Join(
    left: LogicalPlan,
    right: LogicalPlan,
    joinType: JoinType,
    condition: Option[LogicalExpression]
) extends LogicalPlan:
  override def children: Seq[LogicalPlan] = Seq(left, right)
  override def expressions: Seq[LogicalExpression] = condition.toSeq
  override def output: Seq[AttributeReference] = left.output ++ right.output
  override def simpleString: String =
    condition match
      case Some(c) => s"Join[$joinType](${c.simpleString})"
      case None    => s"Join[$joinType]"

final case class Filter(condition: LogicalExpression, child: LogicalPlan) extends LogicalPlan:
  override def children: Seq[LogicalPlan] = Seq(child)
  override def expressions: Seq[LogicalExpression] = Seq(condition)
  override def output: Seq[AttributeReference] = child.output
  override def simpleString: String = s"Filter(${condition.simpleString})"

final case class Project(projectList: Seq[LogicalExpression], child: LogicalPlan) extends LogicalPlan:
  override def children: Seq[LogicalPlan] = Seq(child)
  override def expressions: Seq[LogicalExpression] = projectList
  override def output: Seq[AttributeReference] = projectList.collect { case a: AttributeReference => a }
  override def simpleString: String =
    s"Project(${projectList.map(_.simpleString).mkString(", ")})"
