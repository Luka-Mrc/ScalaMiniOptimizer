# ScalaMiniOptimizer

An educational SQL query optimizer, based on https://github.com/Gravarica/MiniOptimizer, written in Scala 3. It parses a query (MiniQL), builds unresolved/resolved logical plans, applies rule-based logical optimizations, reorders inner/cross joins with a dynamic-programming optimizer, and builds a physical plan with concrete operator choices.

Stage 6 is a physical planning stage. It chooses TableScan or IndexScan access paths, scan/filter/project operators, and NestedLoop or Hash join strategies from the optimized logical plan.

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
ScalaMiniOptimizer - Stage 6 (physical planning).
Komande: tables | desc <ime> | <SQL upit> | X

mini> desc radnik
Tabela: radnik
  rows: 10000
  mbr: IntType (PK, INDEX), ndv=10000
  god: IntType (INDEX), ndv=46
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
Physical plan:
  Project(radnik.mbr#1) [rows=217.39, cost=376.82]
    IndexScan(radnik)(index=god, predicate=radnik.god#2 = 30) [rows=217.39, cost=365.95]
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
      ├─ plans/physical/               PHYSICAL PLANS
      │  └─ PhysicalPlan.scala
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
      ├─ planner/                     PHYSICAL PLANNER
      │  └─ PhysicalPlanner.scala
      ├─ testdata/SampleCatalog.scala deterministic in-memory test catalog
      └─ Main.scala                   entry point (REPL)
```
