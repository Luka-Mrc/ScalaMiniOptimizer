package minioptimizer.ast

import minioptimizer.expressions.DataType


/** A FROM relation: a table name with an optional alias (`radnik AS r` / `radnik r`). */
final case class Relation(name: String, alias: Option[String]):
  /** The qualifier under which this relation's columns are referenced. */
  def qualifier: String = alias.getOrElse(name)

/** A literal constant. */
enum Literal:
  case IntLit(value: Int)
  case DecimalLit(value: Double)
  case StringLit(value: String)
  case NullLit

  /** Data type of the literal; `None` for NULL (compatible with any type). */
  def dataType: Option[DataType] = this match
    case IntLit(_)     => Some(DataType.IntType)
    case DecimalLit(_) => Some(DataType.DecimalType)
    case StringLit(_)  => Some(DataType.StringType)
    case NullLit       => None

/** Arithmetic operators (`* / + -`). */
enum ArithOp:
  case Add, Subtract, Multiply, Divide

/** A scalar expression: column reference, literal, or arithmetic over them. */
enum Expression:
  case Column(table: Option[String], name: String)
  case Lit(value: Literal)
  case BinaryArith(op: ArithOp, left: Expression, right: Expression)

/** Comparison operators (`= != < <= > >=`). */
enum CompOp:
  case Eq, Ne, Lt, Le, Gt, Ge

/** A WHERE predicate: a comparison or a subquery test. */
enum Predicate:
  case Comparison(op: CompOp, left: Expression, right: Expression)
  case InSubquery(expr: Expression, subquery: SelectStatement)
  case Exists(subquery: SelectStatement)

/** The WHERE clause as a boolean tree over predicates (AND / OR). */
enum BoolExpr:
  case And(left: BoolExpr, right: BoolExpr)
  case Or(left: BoolExpr, right: BoolExpr)
  case Pred(predicate: Predicate)

/** A `SELECT projections FROM from [WHERE where]` statement; subqueries nest recursively. */
final case class SelectStatement(
    projections: Seq[Expression],
    from: Seq[Relation],
    where: Option[BoolExpr]
)
