package minioptimizer.catalog

import minioptimizer.expressions.DataType


case class Catalog(tables: Map[String, Table]):

  def table(name: String): Option[Table] = tables.get(name)

  def hasTable(name: String): Boolean = tables.contains(name)

  def hasColumn(tableName: String, columnName: String): Boolean =
    tables.get(tableName).exists(_.hasColumn(columnName))

  def dataTypeOf(tableName: String, columnName: String): Option[DataType] =
    tables.get(tableName).flatMap(_.dataTypeOf(columnName))

  def tablesWithColumn(column: String): Seq[String] =
    tables.collect { case (name, t) if t.hasColumn(column) => name }.toSeq

object Catalog:
  /** Convenience constructor from a list of tables: `Catalog.of(t1, t2, ...)`. */
  def of(tables: Table*): Catalog = Catalog(tables.map(t => t.name -> t).toMap)
