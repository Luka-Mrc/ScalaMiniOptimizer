package minioptimizer.analysis

import minioptimizer.expressions.DataType


enum AnalysisError(val code: Int, val message: String):
  case TableNotFound(name: String)
      extends AnalysisError(800, s"Error 800. Tabela $name nije definisana.")

  case ColumnNotFound(name: String)
      extends AnalysisError(801, s"Error 801. Kolona $name ne postoji.")

  case UnknownTableReference(qualifier: String)
      extends AnalysisError(803, s"Error 803. Referenca na tabelu/alias van dosega: $qualifier.")

  case AmbiguousColumn(name: String, candidates: Seq[String])
      extends AnalysisError(804, s"Error 804. Kolona $name je dvosmislena (kandidati: ${candidates.mkString(", ")}).")

  case TypeMismatch(context: String, left: DataType, right: DataType)
      extends AnalysisError(902, s"Error 902. Nekompatibilni tipovi u $context: $left vs $right.")

  case NonNumericArithmetic(found: DataType)
      extends AnalysisError(903, s"Error 903. Aritmeticka operacija nad nenumerickim tipom: $found.")

  case SubqueryMustReturnOneColumn(actual: Int)
      extends AnalysisError(905, s"Error 905. IN/skalarni podupit mora vratiti tacno jednu kolonu (vraca $actual).")
