package minioptimizer.expressions


enum DataType:
  case IntType
  case DecimalType
  case StringType

object DataType:
  /** Numeric types (participate in arithmetic / range comparisons). */
  def isNumeric(t: DataType): Boolean = t == IntType || t == DecimalType
