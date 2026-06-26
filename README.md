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
ScalaMiniOptimizer — Stage 2 (parser + semanticka analiza).
Komande: tables | desc <ime> | <SQL upit> | X

mini> desc radnik
Tabela: radnik
  mbr: IntType (PK)
  god: IntType
  plt: IntType
mini> SELECT radnik.mbr FROM radnik WHERE radnik.god = 30
OK. AST: SelectStatement(List(radnik.mbr), List(radnik), List(Equals(radnik.god, Const(IntLit(30)))))
mini> X
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
├─ build.sbt                       build definition (Scala version, ANTLR settings)
├─ project/
│  ├─ build.properties             sbt version
│  └─ plugins.sbt                  sbt-antlr4 plugin
├─ docs/                           per-stage documentation (C# ↔ Scala)
│  
└─ src/main/
   ├─ antlr4/MiniQL.g4             GRAMMAR (compiled to lexer/parser at build time)
   └─ scala/minioptimizer/
      ├─ expressions/DataType.scala   value types (INT/DECIMAL/STRING)
      ├─ catalog/                     CATALOG (database schema)
      │  ├─ Column.scala
      │  ├─ Table.scala
      │  └─ Catalog.scala
      ├─ ast/Ast.scala                ABSTRACT SYNTAX TREE (parser output)
      ├─ parser/                      PARSER (ANTLR text -> AST)
      │  ├─ MiniQL.scala              facade: MiniQL text -> SelectStatement
      │  ├─ AstBuilder.scala          parse tree -> AST (visitor)
      │  └─ ParseException.scala      fail-fast syntax errors
      ├─ analysis/                    SEMANTIC ANALYSIS (scope, types, correlation)
      │  ├─ AnalysisError.scala
      │  └─ SemanticAnalyzer.scala
      ├─ testdata/SampleCatalog.scala deterministic in-memory test catalog
      └─ Main.scala                   entry point (REPL)
```
