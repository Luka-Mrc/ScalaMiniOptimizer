package minioptimizer

import minioptimizer.catalog.Catalog
import minioptimizer.testdata.SampleCatalog
import scala.io.StdIn


@main def run(): Unit =
  val catalog = SampleCatalog.all

  println("ScalaMiniOptimizer — Stage 1 (katalog).")
  println("Komande: tables | desc <ime> | X")

  var running = true
  while running do
    print("\ncatalog> ")
    Option(StdIn.readLine()) match
      case None | Some("X") => running = false
      case Some(line) =>
        line.trim.split("\\s+", 2) match
          case Array("tables")        => printTables(catalog)
          case Array("desc", name)    => describe(catalog, name)
          case Array("")              => () // empty line — ignore
          case _                      => println(s"Nepoznata komanda: $line")

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
