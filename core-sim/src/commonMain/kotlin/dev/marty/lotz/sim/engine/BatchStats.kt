package dev.marty.lotz.sim.engine

import kotlin.math.roundToInt

/** A [min, median, mean, p90, p99, max] summary of a batch's per-run Long-valued outcomes. */
data class Distribution(
    val min: Long,
    val median: Long,
    val mean: Double,
    val p90: Long,
    val p99: Long,
    val max: Long,
) {
    companion object {
        /** [values] must be non-empty. Percentiles use nearest-rank on the sorted list. */
        fun of(values: List<Long>): Distribution {
            require(values.isNotEmpty()) { "cannot summarize an empty distribution" }
            val sorted = values.sorted()
            return Distribution(
                min = sorted.first(),
                median = percentile(sorted, 0.5),
                mean = sorted.sum().toDouble() / sorted.size,
                p90 = percentile(sorted, 0.9),
                p99 = percentile(sorted, 0.99),
                max = sorted.last(),
            )
        }

        private fun percentile(sorted: List<Long>, p: Double): Long {
            val index = (p * (sorted.size - 1)).roundToInt().coerceIn(0, sorted.size - 1)
            return sorted[index]
        }
    }
}

/** Same shape as [Distribution] but for Double-valued outcomes (e.g. years-to-jackpot). */
data class DoubleDistribution(
    val min: Double,
    val median: Double,
    val mean: Double,
    val p90: Double,
    val p99: Double,
    val max: Double,
) {
    companion object {
        fun of(values: List<Double>): DoubleDistribution {
            require(values.isNotEmpty()) { "cannot summarize an empty distribution" }
            val sorted = values.sorted()
            return DoubleDistribution(
                min = sorted.first(),
                median = percentile(sorted, 0.5),
                mean = sorted.sum() / sorted.size,
                p90 = percentile(sorted, 0.9),
                p99 = percentile(sorted, 0.99),
                max = sorted.last(),
            )
        }

        private fun percentile(sorted: List<Double>, p: Double): Double {
            val index = (p * (sorted.size - 1)).roundToInt().coerceIn(0, sorted.size - 1)
            return sorted[index]
        }
    }
}

/** One bar of a net-outcome histogram: `[rangeStartCents, rangeEndCentsExclusive)` holds [count] runs. */
data class HistogramBucket(
    val rangeStartCents: Long,
    val rangeEndCentsExclusive: Long,
    val count: Int,
)

/** Only populated for [StopCondition.UntilJackpot] batches: how long/how much it took to win. */
data class UntilJackpotStats(
    val drawingsToJackpot: Distribution,
    val yearsToJackpot: DoubleDistribution,
    val costToJackpotCents: Distribution,
)

/**
 * Aggregate Monte Carlo statistics over a batch of [RunSummary]s that share one [PlayerStrategy].
 * [tierHitRates] is wins-per-drawing-played for each tier key (not wins-per-run), so it is directly
 * comparable to that tier's published odds.
 */
data class BatchStats(
    val runs: Int,
    val netCentsDistribution: Distribution,
    val spentCentsDistribution: Distribution,
    val drawingsPlayedDistribution: Distribution,
    val netHistogram: List<HistogramBucket>,
    val probabilityOfProfit: Double,
    val expectedLossPerDollar: Double,
    val tierHitRates: Map<String, Double>,
    val jackpotWinFraction: Double,
    val untilJackpot: UntilJackpotStats?,
    /** False when runs came from [AnalyticSimulator]: won/net/histogram are meaningless zeros. */
    val winningsTracked: Boolean,
    /** Runs in which the player hit the jackpot at least once. */
    val jackpotWinners: Int,
    /** Sum of drawings played across all runs. */
    val totalDrawingsPlayed: Long,
    /** Sum of each tier's win count across all runs (Long: analytic runs can span millennia). */
    val tierTotalCounts: Map<String, Long>,
) {
    companion object {
        private const val HISTOGRAM_BUCKET_COUNT = 20

        fun from(strategy: PlayerStrategy, summaries: List<RunSummary>): BatchStats {
            require(summaries.isNotEmpty()) { "cannot compute stats over zero runs" }

            val netValues = summaries.map { it.netCents }
            val spentValues = summaries.map { it.totalSpentCents }
            val drawingsValues = summaries.map { it.drawingsPlayed.toLong() }

            val totalSpent = spentValues.sum()
            val totalWon = summaries.sumOf { it.totalWonCents }
            val totalDrawings = drawingsValues.sum()

            val tierTotalCounts = summaries
                .flatMap { it.tierWinCounts.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, counts) -> counts.sumOf { count -> count.toLong() } }
            val tierHitRates = tierTotalCounts.mapValues { (_, count) -> count.toDouble() / totalDrawings }

            val untilJackpot = if (strategy.stopCondition is StopCondition.UntilJackpot) {
                val years = summaries.map { it.yearsElapsed }
                UntilJackpotStats(
                    drawingsToJackpot = Distribution.of(drawingsValues),
                    yearsToJackpot = DoubleDistribution.of(years),
                    costToJackpotCents = Distribution.of(spentValues),
                )
            } else {
                null
            }

            return BatchStats(
                runs = summaries.size,
                netCentsDistribution = Distribution.of(netValues),
                spentCentsDistribution = Distribution.of(spentValues),
                drawingsPlayedDistribution = Distribution.of(drawingsValues),
                netHistogram = histogramOf(netValues, HISTOGRAM_BUCKET_COUNT),
                probabilityOfProfit = summaries.count { it.netCents > 0 }.toDouble() / summaries.size,
                expectedLossPerDollar = if (totalSpent > 0) (totalSpent - totalWon).toDouble() / totalSpent else 0.0,
                tierHitRates = tierHitRates,
                jackpotWinFraction = summaries.count { it.jackpotWon }.toDouble() / summaries.size,
                untilJackpot = untilJackpot,
                winningsTracked = summaries.all { it.winningsTracked },
                jackpotWinners = summaries.count { it.jackpotWon },
                totalDrawingsPlayed = totalDrawings,
                tierTotalCounts = tierTotalCounts,
            )
        }

        private fun histogramOf(values: List<Long>, bucketCount: Int): List<HistogramBucket> {
            val min = values.min()
            val max = values.max()
            if (min == max) {
                return listOf(HistogramBucket(min, max + 1, values.size))
            }
            val span = max - min
            val bucketWidth = maxOf(1L, span / bucketCount + if (span % bucketCount != 0L) 1 else 0)

            val buckets = LinkedHashMap<Long, Int>()
            var start = min
            while (start <= max) {
                buckets[start] = 0
                start += bucketWidth
            }
            for (value in values) {
                val bucketStart = min + ((value - min) / bucketWidth) * bucketWidth
                buckets[bucketStart] = (buckets[bucketStart] ?: 0) + 1
            }
            return buckets.entries.sortedBy { it.key }.map { (start, count) ->
                HistogramBucket(start, start + bucketWidth, count)
            }
        }
    }
}
