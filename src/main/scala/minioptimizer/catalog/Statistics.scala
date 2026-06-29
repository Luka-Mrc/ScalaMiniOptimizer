package minioptimizer.catalog


final case class TableStatistics(
    rowCount: Double,
    columnStats: Map[String, ColumnStatistics] = Map.empty
):
  def forColumn(name: String): Option[ColumnStatistics] =
    columnStats.get(name)

final case class ColumnStatistics(
    distinctCount: Option[Double] = None,
    nullCount: Option[Double] = None,
    min: Option[Double] = None,
    max: Option[Double] = None,
    histogram: Option[Histogram] = None
)

final case class HistogramBucket(
    lower: Double,
    upper: Double,
    rowCount: Double,
    distinctCount: Option[Double] = None
):
  require(upper >= lower, "Histogram bucket upper bound must be >= lower bound.")
  require(rowCount >= 0, "Histogram bucket row count must be non-negative.")

final case class Histogram(buckets: Seq[HistogramBucket]):
  require(buckets.nonEmpty, "Histogram must contain at least one bucket.")

  val totalRows: Double = buckets.map(_.rowCount).sum

  def equalityRows(value: Double): Double =
    buckets
      .find(bucket => value >= bucket.lower && value <= bucket.upper)
      .map { bucket =>
        val ndv = bucket.distinctCount.getOrElse(math.max(1.0, bucket.upper - bucket.lower + 1.0))
        bucket.rowCount / math.max(1.0, ndv)
      }
      .getOrElse(0.0)

  def rangeRows(keep: Double => Boolean): Double =
    buckets.map(bucketRowsInRange(_, keep)).sum

  private def bucketRowsInRange(bucket: HistogramBucket, keep: Double => Boolean): Double =
    val lowerKept = keep(bucket.lower)
    val upperKept = keep(bucket.upper)

    if lowerKept && upperKept then bucket.rowCount
    else if !lowerKept && !upperKept then 0.0
    else
      val width = math.max(1.0, bucket.upper - bucket.lower)
      val samples = 20
      val kept = (0 to samples).count { i =>
        val value = bucket.lower + (width * i / samples.toDouble)
        keep(value)
      }
      bucket.rowCount * kept.toDouble / (samples + 1).toDouble
