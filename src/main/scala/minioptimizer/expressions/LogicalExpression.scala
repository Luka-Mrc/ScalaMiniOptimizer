package minioptimizer.expressions

import minioptimizer.ast.{ArithOp, CompOp, Literal}
import minioptimizer.plans.logical.LogicalPlan

sealed trait LogicalExpression:
  def children: Seq[LogicalExpression] = Nil
  def dataType: Option[DataType]
  def resolved: Boolean = children.forall(_.resolved)
  def simpleString: String = toString

final case class UnresolvedAttribute(table: Option[String], name: String) extends LogicalExpression:
  override def dataType: Option[DataType] = None
  override def resolved: Boolean = false
  override def simpleString: String = table.map(t => s"$t.$name").getOrElse(name)

final case class AttributeReference(
    qualifier: String,
    name: String,
    override val dataType: Option[DataType],
    exprId: Long = ExprId.next()
) extends LogicalExpression:
  override def simpleString: String = s"$qualifier.$name#$exprId"

object ExprId:
  private var current: Long = 0L

  def next(): Long =
    current += 1L
    current

final case class LiteralExpression(value: Literal) extends LogicalExpression:
  override def dataType: Option[DataType] = value.dataType
  override def simpleString: String = value match
    case Literal.IntLit(v)     => v.toString
    case Literal.DecimalLit(v) => v.toString
    case Literal.StringLit(v)  => s"'$v'"
    case Literal.NullLit       => "NULL"

final case class ArithmeticExpression(
    op: ArithOp,
    left: LogicalExpression,
    right: LogicalExpression,
    resultType: Option[DataType] = None
) extends LogicalExpression:
  override def children: Seq[LogicalExpression] = Seq(left, right)
  override def dataType: Option[DataType] = resultType
  override def simpleString: String = s"(${left.simpleString} ${opString(op)} ${right.simpleString})"

final case class ComparisonExpression(
    op: CompOp,
    left: LogicalExpression,
    right: LogicalExpression
) extends LogicalExpression:
  override def children: Seq[LogicalExpression] = Seq(left, right)
  override def dataType: Option[DataType] = None
  override def simpleString: String = s"${left.simpleString} ${opString(op)} ${right.simpleString}"

final case class AndExpression(left: LogicalExpression, right: LogicalExpression) extends LogicalExpression:
  override def children: Seq[LogicalExpression] = Seq(left, right)
  override def dataType: Option[DataType] = None
  override def simpleString: String = s"(${left.simpleString} AND ${right.simpleString})"

final case class OrExpression(left: LogicalExpression, right: LogicalExpression) extends LogicalExpression:
  override def children: Seq[LogicalExpression] = Seq(left, right)
  override def dataType: Option[DataType] = None
  override def simpleString: String = s"(${left.simpleString} OR ${right.simpleString})"

final case class InSubqueryExpression(expr: LogicalExpression, subquery: LogicalPlan) extends LogicalExpression:
  override def children: Seq[LogicalExpression] = Seq(expr)
  override def dataType: Option[DataType] = None
  override def resolved: Boolean = expr.resolved && subquery.resolved
  override def simpleString: String = s"${expr.simpleString} IN (${subquery.simpleString})"

final case class ExistsExpression(subquery: LogicalPlan) extends LogicalExpression:
  override def dataType: Option[DataType] = None
  override def resolved: Boolean = subquery.resolved
  override def simpleString: String = s"EXISTS (${subquery.simpleString})"

private def opString(op: ArithOp): String = op match
  case ArithOp.Add      => "+"
  case ArithOp.Subtract => "-"
  case ArithOp.Multiply => "*"
  case ArithOp.Divide   => "/"

private def opString(op: CompOp): String = op match
  case CompOp.Eq => "="
  case CompOp.Ne => "!="
  case CompOp.Lt => "<"
  case CompOp.Le => "<="
  case CompOp.Gt => ">"
  case CompOp.Ge => ">="
