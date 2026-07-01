package minioptimizer.catalog

import minioptimizer.expressions.DataType


case class Column(
    name: String,
    dataType: DataType,
    isNullable: Boolean = false,
    isPrimaryKey: Boolean = false,
    isIndexed: Boolean = false
)
