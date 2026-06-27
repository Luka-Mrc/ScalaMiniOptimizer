package minioptimizer

import minioptimizer.catalog.Catalog
import minioptimizer.testdata.SampleCatalog
import minioptimizer.parser.{MiniQL, ParseException}
import minioptimizer.analysis.{SemanticAnalyzer, AnalysisReport}
import scala.io.StdIn


@main def run(): Unit =
  val catalog  = SampleCatalog.all
  val analyzer = SemanticAnalyzer(catalog)

  println("ScalaMiniOptimizer — Stage 3 (unresolved/resolved logical plan).")
  println("Komande: tables | desc <ime> | <SQL upit> | X")

  var running = true
  while running do
    print("\nmini> ")
    Option(StdIn.readLine()) match
      case None | Some("X") => running = false
      case Some(line) =>
        val trimmed = line.trim
        trimmed.split("\\s+", 2) match
          case Array("")                  => () // empty line — ignore
          case Array("tables")            => printTables(catalog)
          case Array("desc", name)        => describe(catalog, name)
          case _ if isQuery(trimmed)      => runQuery(analyzer, trimmed)
          case _                          => println(s"Nepoznata komanda: $line")

/** A line is treated as a query if it starts with SELECT (any case). */
private def isQuery(line: String): Boolean =
  line.toLowerCase.startsWith("select")

/** Parse and analyze a query, printing the AST + correlations or the first error. */
private def runQuery(analyzer: SemanticAnalyzer, sql: String): Unit =
  val parsed =
    try Right(MiniQL.parse(sql))
    catch case e: ParseException => Left(e.getMessage)

  parsed match
    case Left(err) => println(s"Sintaksna greska: $err")
    case Right(stmt) =>
      analyzer.analyze(stmt) match
        case Left(err)     => println(s"Semanticka greska: ${err.message}")
        case Right(report) => printReport(report)

private def printReport(report: AnalysisReport): Unit =
  println("\nAST:")
  println(s"  ${report.statement}")
  println("\nUnresolved logical plan:")
  println(indent(report.unresolved.treeString))
  println("\nResolved logical plan:")
  println(indent(report.resolved.treeString))
  if report.correlations.nonEmpty then
    println("Korelisane reference (podupit -> spoljasnji upit):")
    for c <- report.correlations do
      println(s"  ${c.qualifier}.${c.column} (dubina ${c.depth})")

private def indent(text: String): String =
  text.linesIterator.map(line => s"  $line").mkString("\n")

/** List the names of all tables in the catalog (sorted for stable output). */
private def printTables(catalog: Catalog): Unit =
  catalog.tables.keys.toSeq.sorted.foreach(name => println(s"  $name"))

/** Show the columns of a single table, with type and primary-key marker. */
private def describe(catalog: Catalog, name: String): Unit =
  catalog.table(name) match
    case None => println(s"Tabela ne postoji: $name")
    case Some(t) =>
      println(s"Tabela: ${t.name}")
      for c <- t.columns do
        val pk = if c.isPrimaryKey then " (PK)" else ""
        println(s"  ${c.name}: ${c.dataType}$pk")
