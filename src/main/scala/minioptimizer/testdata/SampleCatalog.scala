package minioptimizer.testdata

import minioptimizer.catalog.*
import minioptimizer.expressions.DataType


object SampleCatalog:

  /** Helper: make an INT column, optionally a primary key. */
  private def col(name: String, pk: Boolean = false): Column =
    Column(name, DataType.IntType, isPrimaryKey = pk)

  private def table(name: String, rowCount: Double, columns: Seq[Column])(
      stats: (String, ColumnStatistics)*
  ): Table =
    Table(name, columns, Some(TableStatistics(rowCount, stats.toMap)))

  private def intStats(rowCount: Double, distinctCount: Double, min: Double, max: Double): ColumnStatistics =
    ColumnStatistics(
      distinctCount = Some(distinctCount),
      min = Some(min),
      max = Some(max),
      histogram = Some(uniformHistogram(rowCount, distinctCount, min, max))
    )

  private def uniformHistogram(rowCount: Double, distinctCount: Double, min: Double, max: Double): Histogram =
    val bucketCount = 4
    val width = math.max(1.0, (max - min + 1.0) / bucketCount.toDouble)
    Histogram((0 until bucketCount).map { i =>
      val lower = min + width * i
      val upper = if i == bucketCount - 1 then max else math.min(max, lower + width - 1.0)
      HistogramBucket(
        lower = lower,
        upper = upper,
        rowCount = rowCount / bucketCount.toDouble,
        distinctCount = Some(math.max(1.0, distinctCount / bucketCount.toDouble))
      )
    })

  /** Four "Serbian" tables (radnik / projekat / radproj / angazovanje). */
  val sample: Catalog = Catalog.of(
    table(
      "radnik",
      rowCount = 10000,
      columns = Seq(col("mbr", pk = true), col("god"), col("plt"))
    )(
      "mbr" -> intStats(10000, 10000, 1, 10000),
      "god" -> intStats(10000, 46, 20, 65),
      "plt" -> intStats(10000, 600, 30000, 180000)
    ),
    table(
      "projekat",
      rowCount = 800,
      columns = Seq(col("spr", pk = true), col("ruk"), col("trajanje"))
    )(
      "spr" -> intStats(800, 800, 1, 800),
      "ruk" -> intStats(800, 200, 1, 10000),
      "trajanje" -> intStats(800, 36, 1, 36)
    ),
    table(
      "radproj",
      rowCount = 25000,
      columns = Seq(col("mbr", pk = true), col("spr", pk = true), col("brc"))
    )(
      "mbr" -> intStats(25000, 9000, 1, 10000),
      "spr" -> intStats(25000, 750, 1, 800),
      "brc" -> intStats(25000, 20, 1, 20)
    ),
    table(
      "angazovanje",
      rowCount = 16000,
      columns = Seq(col("mbr", pk = true), col("brp"))
    )(
      "mbr" -> intStats(16000, 9000, 1, 10000),
      "brp" -> intStats(16000, 300, 1, 300)
    )
  )

  /** Generic A/B/C/D tables (used by sample query 2). */
  val abcd: Catalog = Catalog.of(
    table(
      "A",
      rowCount = 12000,
      columns = Seq(col("a1", pk = true), col("b1"), col("c1"))
    )(
      "a1" -> intStats(12000, 12000, 1, 12000),
      "b1" -> intStats(12000, 3000, 1, 6000),
      "c1" -> intStats(12000, 500, 1, 500)
    ),
    table(
      "B",
      rowCount = 6000,
      columns = Seq(col("b1", pk = true), col("c1"), col("d1"))
    )(
      "b1" -> intStats(6000, 6000, 1, 6000),
      "c1" -> intStats(6000, 500, 1, 500),
      "d1" -> intStats(6000, 1000, 1, 1000)
    ),
    table(
      "C",
      rowCount = 30000,
      columns = Seq(col("a1", pk = true), col("f1", pk = true), col("e1"))
    )(
      "a1" -> intStats(30000, 12000, 1, 12000),
      "f1" -> intStats(30000, 15000, 1, 15000),
      "e1" -> intStats(30000, 2000, 1, 2000)
    ),
    table(
      "D",
      rowCount = 15000,
      columns = Seq(col("f1", pk = true), col("g1"))
    )(
      "f1" -> intStats(15000, 15000, 1, 15000),
      "g1" -> intStats(15000, 700, 1, 700)
    )
  )

  /** All tables together (for the REPL / mixed tests). */
  val all: Catalog = Catalog(sample.tables ++ abcd.tables)
