package minioptimizer.parser

import scala.jdk.CollectionConverters.*

import minioptimizer.parser.generated.MiniQLParser.*
import minioptimizer.ast.*


object AstBuilder:

  def buildQuery(ctx: QueryContext): SelectStatement =
    val projections = ctx.selectList.expression.asScala.map(buildExpression).toSeq
    val relations   = ctx.relationList.relation.asScala.map(buildRelation).toSeq
    val where       = Option(ctx.whereExpr).map(buildWhere)
    SelectStatement(projections, relations, where)

  private def buildRelation(ctx: RelationContext): Relation =
    val ids   = ctx.IDENTIFIER
    val name  = ids.get(0).getText
    val alias = if ids.size > 1 then Some(ids.get(1).getText) else None
    Relation(name, alias)

  private def buildWhere(ctx: WhereExprContext): BoolExpr = ctx match
    case c: AndExprContext    => BoolExpr.And(buildWhere(c.whereExpr(0)), buildWhere(c.whereExpr(1)))
    case c: OrExprContext     => BoolExpr.Or(buildWhere(c.whereExpr(0)), buildWhere(c.whereExpr(1)))
    case c: ParenWhereContext => buildWhere(c.whereExpr)
    case c: PredWhereContext  => BoolExpr.Pred(buildPredicate(c.predicate))
    case _ => throw new ParseException(s"Neocekivani WHERE cvor: ${ctx.getText}")

  private def buildPredicate(ctx: PredicateContext): Predicate = ctx match
    case c: ComparisonContext =>
      val left  = buildExpression(c.expression(0))
      val right = buildExpression(c.expression(1))
      val op =
        if c.EQ != null then CompOp.Eq
        else if c.NE != null then CompOp.Ne
        else if c.LT != null then CompOp.Lt
        else if c.LE != null then CompOp.Le
        else if c.GT != null then CompOp.Gt
        else CompOp.Ge
      Predicate.Comparison(op, left, right)
    case c: InSubqueryContext =>
      Predicate.InSubquery(buildExpression(c.expression), buildQuery(c.query))
    case c: ExistsSubqueryContext =>
      Predicate.Exists(buildQuery(c.query))
    case _ => throw new ParseException(s"Neocekivani predikat: ${ctx.getText}")

  private def buildExpression(ctx: ExpressionContext): Expression = ctx match
    case c: MulDivContext =>
      val op = if c.ASTERISK != null then ArithOp.Multiply else ArithOp.Divide
      Expression.BinaryArith(op, buildExpression(c.left), buildExpression(c.right))
    case c: AddSubContext =>
      val op = if c.PLUS != null then ArithOp.Add else ArithOp.Subtract
      Expression.BinaryArith(op, buildExpression(c.left), buildExpression(c.right))
    case c: ParenArithContext => buildExpression(c.expression)
    case c: ColumnRefContext  => buildIdentifier(c.identifier)
    case c: ConstRefContext   => Expression.Lit(buildConstant(c.constant))
    case _ => throw new ParseException(s"Neocekivani izraz: ${ctx.getText}")

  private def buildIdentifier(ctx: IdentifierContext): Expression =
    ctx.IDENTIFIER.asScala.map(_.getText).toSeq match
      case Seq(col)      => Expression.Column(None, col)
      case Seq(tbl, col) => Expression.Column(Some(tbl), col)
      case _             => throw new ParseException(s"Neispravan identifikator: ${ctx.getText}")

  private def buildConstant(ctx: ConstantContext): Literal = ctx match
    case c: NumericConstContext =>
      val t = c.NUMERIC_LITERAL.getText
      if t.contains(".") then Literal.DecimalLit(t.toDouble) else Literal.IntLit(t.toInt)
    case c: StringConstContext =>
      val raw = c.QUOTED_STRING.getText
      Literal.StringLit(raw.substring(1, raw.length - 1)) // strip surrounding quotes
    case _: NullConstContext => Literal.NullLit
    case _ => throw new ParseException(s"Neocekivana konstanta: ${ctx.getText}")
