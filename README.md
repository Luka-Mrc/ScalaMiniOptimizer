# ScalaMiniOptimizer

An educational SQL query optimizer, based on https://github.com/Gravarica/MiniOptimizer, written in Scala 3. It parses a query (MiniQL), builds unresolved/resolved logical plans, applies rule-based logical optimizations, and reorders inner/cross joins with a dynamic-programming optimizer driven by catalog statistics and cardinality/cost estimates.

Stage 5 is a cost-based logical join-order optimization stage. It does not generate physical plans yet.

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
ScalaMiniOptimizer - Stage 5 (cost-based logical join-order optimization).
Komande: tables | desc <ime> | <SQL upit> | X

mini> desc radnik
Tabela: radnik
  rows: 10000
  mbr: IntType (PK), ndv=10000
  god: IntType, ndv=46
  plt: IntType, ndv=600
mini> SELECT radnik.mbr FROM radnik WHERE radnik.god = 30
AST:
  SelectStatement(List(Column(Some(radnik),mbr)),List(Relation(radnik,None)),Some(Pred(Comparison(Eq,Column(Some(radnik),god),Lit(IntLit(30))))))
Unresolved logical plan:
  Project(radnik.mbr)
    Filter(radnik.god = 30)
      UnresolvedRelation(radnik)
Resolved logical plan:
  Project(radnik.mbr#1)
    Filter(radnik.god#2 = 30)
      Scan(radnik)
Rule-based optimized logical plan:
  Project(radnik.mbr#1)
    Filter(radnik.god#2 = 30)
      Scan(radnik)
Join-order optimized logical plan:
  Project(radnik.mbr#1)
    Filter(radnik.god#2 = 30)
      Scan(radnik)
Estimated join-order optimized logical plan:
  Project(radnik.mbr#1) [rows=217.39, cost=12.01k]
    Filter(radnik.god#2 = 30) [rows=217.39, cost=12.00k]
      Scan(radnik) [rows=10.00k, cost=10.00k]
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
      ├─ expressions/                  typed logical expressions
      │  ├─ DataType.scala             value types (INT/DECIMAL/STRING)
      │  └─ LogicalExpression.scala    unresolved/resolved expressions
      ├─ catalog/                     CATALOG (database schema)
      │  ├─ Column.scala
      │  ├─ Table.scala
      │  ├─ Statistics.scala
      │  └─ Catalog.scala
      ├─ ast/Ast.scala                ABSTRACT SYNTAX TREE (parser output)
      ├─ parser/                      PARSER (ANTLR text -> AST)
      │  ├─ MiniQL.scala              facade: MiniQL text -> SelectStatement
      │  ├─ AstBuilder.scala          parse tree -> AST (visitor)
      │  └─ ParseException.scala      fail-fast syntax errors
      ├─ plans/logical/                LOGICAL PLANS (unresolved -> resolved)
      │  ├─ LogicalPlan.scala
      │  └─ LogicalPlanBuilder.scala
      ├─ analysis/                    SEMANTIC ANALYSIS (scope, types, correlation)
      │  ├─ AnalysisError.scala
      │  └─ SemanticAnalyzer.scala
      ├─ optimizer/                   LOGICAL OPTIMIZER (rules + fixed point execution)
      │  ├─ Rule.scala
      │  ├─ RuleExecutor.scala
      │  ├─ RuleBasedOptimizer.scala
      │  ├─ JoinOrderOptimizer.scala
      │  └─ rules/
      ├─ cost/                        CARDINALITY/COST ESTIMATION
      │  └─ CostEstimator.scala
      ├─ testdata/SampleCatalog.scala deterministic in-memory test catalog
      └─ Main.scala                   entry point (REPL)
```
