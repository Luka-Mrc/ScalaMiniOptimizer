# ScalaMiniOptimizer

An educational SQL query optimizer, based on https://github.com/Gravarica/MiniOptimizer, written in Scala 3. It parses a query (MiniQL), builds a logical plan, applies rule-based and cost-based
optimizations, and emits a physical plan.

## Prerequisites

- **JDK** 11+ (17 recommended)
- **sbt** 1.10.7 — [installation](https://www.scala-sbt.org/download)
- **Scala** 3.3.4 — sbt downloads it automatically on first run

## Running

From the project root (where `build.sbt` is):

```bash
sbt run
```

On the first run sbt downloads Scala and its dependencies (a few minutes);
afterwards it is cached.

Once started, you get a REPL:

```
ScalaMiniOptimizer — Stage 1 (katalog).
Komande: tables | desc <ime> | X

catalog> tables
  A
  B
  ...
catalog> desc radnik
Tabela: radnik
  mbr: IntType (PK)
  god: IntType
  plt: IntType
catalog> X
```

### Useful commands

| Command | Description |
|---|---|
| `sbt run` | run the application (REPL) |
| `sbt compile` | compile only (check without running) |
| `sbt test` | run tests (from later stages) |
| `sbt` | interactive sbt console (then type `run`, `compile`, `reload`) |

## Project structure

```
ScalaMiniOptimizer/
├─ build.sbt                       build definition (Scala version, dependencies)
├─ project/build.properties        sbt version
├─ docs/                           per-stage documentation (C# ↔ Scala)
│  └─ 01-arhitektura-i-katalog.md
└─ src/main/scala/minioptimizer/
   ├─ expressions/DataType.scala   value types (INT/DECIMAL/STRING)
   ├─ catalog/                     CATALOG (database schema)
   │  ├─ Column.scala
   │  ├─ Table.scala
   │  └─ Catalog.scala
   ├─ testdata/SampleCatalog.scala deterministic in-memory test catalog
   └─ Main.scala                   entry point (REPL)
```
