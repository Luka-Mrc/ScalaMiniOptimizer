package minioptimizer.testdata

import minioptimizer.catalog.*
import minioptimizer.expressions.DataType


object SampleCatalog:

  /** Helper: make an INT column, optionally a primary key. */
  private def col(name: String, pk: Boolean = false): Column =
    Column(name, DataType.IntType, isPrimaryKey = pk)

  /** Four "Serbian" tables (radnik / projekat / radproj / angazovanje). */
  val sample: Catalog = Catalog.of(
    Table("radnik",      Seq(col("mbr", pk = true), col("god"), col("plt"))),
    Table("projekat",    Seq(col("spr", pk = true), col("ruk"), col("trajanje"))),
    // radproj has a composite primary key (mbr, spr).
    Table("radproj",     Seq(col("mbr", pk = true), col("spr", pk = true), col("brc"))),
    Table("angazovanje", Seq(col("mbr", pk = true), col("brp")))
  )

  /** Generic A/B/C/D tables (used by sample query 2). */
  val abcd: Catalog = Catalog.of(
    Table("A", Seq(col("a1", pk = true), col("b1"), col("c1"))),
    Table("B", Seq(col("b1", pk = true), col("c1"), col("d1"))),
    Table("C", Seq(col("a1", pk = true), col("f1", pk = true), col("e1"))),
    Table("D", Seq(col("f1", pk = true), col("g1")))
  )

  /** All tables together (for the REPL / mixed tests). */
  val all: Catalog = Catalog(sample.tables ++ abcd.tables)
