package minioptimizer.catalog

import minioptimizer.expressions.DataType


case class Table(
    name: String,
    columns: Seq[Column],
    statistics: Option[TableStatistics] = None
):

  // Computed once at construction: column name -> column.
  private val byName: Map[String, Column] = columns.map(c => c.name -> c).toMap

  def column(name: String): Option[Column] = byName.get(name)

  def hasColumn(name: String): Boolean = byName.contains(name)

  def dataTypeOf(name: String): Option[DataType] = byName.get(name).map(_.dataType)

  def statsOf(name: String): Option[ColumnStatistics] =
    statistics.flatMap(_.forColumn(name))

  /** Columns that make up the primary key (may be composite, e.g. radproj = mbr+spr). */
  def primaryKey: Seq[Column] = columns.filter(_.isPrimaryKey)

  /** Primary-key columns are treated as indexed access paths. */
  def isIndexedColumn(name: String): Boolean =
    byName.get(name).exists(column => column.isIndexed || column.isPrimaryKey)

  def indexedColumns: Seq[Column] =
    columns.filter(column => column.isIndexed || column.isPrimaryKey)
