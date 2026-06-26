package minioptimizer.analysis

import minioptimizer.ast.*
import minioptimizer.catalog.{Catalog, Table, Column}
import minioptimizer.expressions.DataType
import scala.collection.mutable.ListBuffer

/** A correlated reference: a column inside a subquery that binds to an outer query. */
final case class CorrelatedRef(column: String, qualifier: String, depth: Int)

/** Result of a successful analysis: the validated statement and any correlations found. */
final case class AnalysisReport(statement: SelectStatement, correlations: Seq[CorrelatedRef])


final class SemanticAnalyzer(catalog: Catalog):

  /** A resolved FROM relation: its qualifier (alias or name) and catalog table. */
  private final case class Bound(qualifier: String, table: Table)

  /** One nesting level's visible relations. */
  private final case class Level(relations: Seq[Bound])

  /** A resolved column: its qualifier, the catalog column, and scope depth (0 = current). */
  private final case class Resolved(qualifier: String, column: Column, depth: Int)

  def analyze(stmt: SelectStatement): Either[AnalysisError, AnalysisReport] =
    val correlations = ListBuffer.empty[CorrelatedRef]
    analyzeLevel(stmt, Nil, correlations).map(_ => AnalysisReport(stmt, correlations.toList))

  /** Validate one query level; returns the scope chain (innermost first) on success. */
  private def analyzeLevel(
      stmt: SelectStatement,
      outer: List[Level],
      corr: ListBuffer[CorrelatedRef]
  ): Either[AnalysisError, List[Level]] =
    for
      level <- resolveFrom(stmt.from)
      scope  = level :: outer
      _ <- traverse(stmt.projections)(exprType(_, scope, corr)) // type-check projections
      _ <- stmt.where match
             case Some(b) => checkBool(b, scope, corr)
             case None    => Right(())
    yield scope

  private def resolveFrom(relations: Seq[Relation]): Either[AnalysisError, Level] =
    traverse(relations) { rel =>
      catalog.table(rel.name)
        .toRight(AnalysisError.TableNotFound(rel.name))
        .map(t => Bound(rel.qualifier, t))
    }.map(bs => Level(bs))

  private def checkBool(
      b: BoolExpr,
      scope: List[Level],
      corr: ListBuffer[CorrelatedRef]
  ): Either[AnalysisError, Unit] =
    b match
      case BoolExpr.And(l, r) => checkBool(l, scope, corr).flatMap(_ => checkBool(r, scope, corr))
      case BoolExpr.Or(l, r)  => checkBool(l, scope, corr).flatMap(_ => checkBool(r, scope, corr))
      case BoolExpr.Pred(p)   => checkPredicate(p, scope, corr)

  private def checkPredicate(
      pred: Predicate,
      scope: List[Level],
      corr: ListBuffer[CorrelatedRef]
  ): Either[AnalysisError, Unit] =
    pred match
      case Predicate.Comparison(_, left, right) =>
        for
          lt <- exprType(left, scope, corr)
          rt <- exprType(right, scope, corr)
          _  <- compatible("predikatu", lt, rt)
        yield ()

      case Predicate.InSubquery(expr, q) =>
        for
          et <- exprType(expr, scope, corr)
          st <- scalarSubqueryType(q, scope, corr)
          _  <- compatible("IN podupitu", et, st)
        yield ()

      case Predicate.Exists(q) =>
        // EXISTS ignores the subquery's projection list — just validate it.
        analyzeLevel(q, scope, corr).map(_ => ())

  /** Analyze a single-column subquery and return its projected column's type. */
  private def scalarSubqueryType(
      q: SelectStatement,
      outer: List[Level],
      corr: ListBuffer[CorrelatedRef]
  ): Either[AnalysisError, Option[DataType]] =
    for
      scope <- analyzeLevel(q, outer, corr)
      _     <- Either.cond(q.projections.size == 1, (),
                 AnalysisError.SubqueryMustReturnOneColumn(q.projections.size))
      t     <- exprType(q.projections.head, scope, corr)
    yield t

  /**
   * Infer the type of an expression. `None` means NULL / untyped (compatible
   * with any type). Records a correlation when a column binds to an outer scope.
   */
  private def exprType(
      expr: Expression,
      scope: List[Level],
      corr: ListBuffer[CorrelatedRef]
  ): Either[AnalysisError, Option[DataType]] =
    expr match
      case Expression.Lit(lit) => Right(lit.dataType)
      case Expression.Column(tbl, name) =>
        resolveCol(tbl, name, scope, corr).map(r => Some(r.column.dataType))
      case Expression.BinaryArith(_, l, r) =>
        for
          lt  <- exprType(l, scope, corr)
          rt  <- exprType(r, scope, corr)
          res <- arithResult(lt, rt)
        yield res

  /** Result type of an arithmetic op; both operands must be numeric (or NULL). */
  private def arithResult(
      lt: Option[DataType],
      rt: Option[DataType]
  ): Either[AnalysisError, Option[DataType]] =
    def requireNumeric(o: Option[DataType]): Either[AnalysisError, Unit] = o match
      case Some(t) if !DataType.isNumeric(t) => Left(AnalysisError.NonNumericArithmetic(t))
      case _                                 => Right(())
    for
      _ <- requireNumeric(lt)
      _ <- requireNumeric(rt)
    yield (lt, rt) match
      case (Some(DataType.DecimalType), _) | (_, Some(DataType.DecimalType)) => Some(DataType.DecimalType)
      case (Some(DataType.IntType), _)     | (_, Some(DataType.IntType))     => Some(DataType.IntType)
      case _                                                                 => None

  /** Two operand types are compatible if either is NULL, both numeric, or equal. */
  private def compatible(
      context: String,
      lt: Option[DataType],
      rt: Option[DataType]
  ): Either[AnalysisError, Unit] =
    (lt, rt) match
      case (None, _) | (_, None) => Right(())
      case (Some(a), Some(b)) =>
        if a == b || (DataType.isNumeric(a) && DataType.isNumeric(b)) then Right(())
        else Left(AnalysisError.TypeMismatch(context, a, b))

  /** Resolve a column, recording a correlation if it binds to an outer scope. */
  private def resolveCol(
      table: Option[String],
      name: String,
      scope: List[Level],
      corr: ListBuffer[CorrelatedRef]
  ): Either[AnalysisError, Resolved] =
    resolveColRaw(table, name, scope).map { r =>
      if r.depth > 0 then corr += CorrelatedRef(name, r.qualifier, r.depth)
      r
    }

  private def resolveColRaw(
      table: Option[String],
      name: String,
      scope: List[Level]
  ): Either[AnalysisError, Resolved] =
    table match
      // Qualified `q.column`: find the scope level holding the relation with that qualifier.
      case Some(q) =>
        scope.zipWithIndex.collectFirst {
          case (lvl, d) if lvl.relations.exists(_.qualifier == q) => (lvl, d)
        } match
          case None => Left(AnalysisError.UnknownTableReference(q))
          case Some((lvl, depth)) =>
            val bound = lvl.relations.find(_.qualifier == q).get
            bound.table.column(name) match
              case None    => Left(AnalysisError.ColumnNotFound(s"$q.$name"))
              case Some(c) => Right(Resolved(q, c, depth))

      // Unqualified column: search all relations across the scope chain (inner first).
      case None =>
        val hits =
          scope.zipWithIndex.flatMap { case (lvl, depth) =>
            lvl.relations.flatMap(b => b.table.column(name).map(c => Resolved(b.qualifier, c, depth)))
          }
        hits match
          case Nil => Left(AnalysisError.ColumnNotFound(name))
          case _ =>
            // The innermost scope shadows outer ones; ambiguity is only within it.
            val minDepth = hits.map(_.depth).min
            hits.filter(_.depth == minDepth) match
              case single :: Nil => Right(single)
              case many          => Left(AnalysisError.AmbiguousColumn(name, many.map(_.qualifier)))

  /** Sequence Either-returning checks, short-circuiting on the first error. */
  private def traverse[A, B](xs: Seq[A])(f: A => Either[AnalysisError, B]): Either[AnalysisError, Seq[B]] =
    xs.foldRight[Either[AnalysisError, List[B]]](Right(Nil)) { (a, acc) =>
      for b <- f(a); bs <- acc yield b :: bs
    }
