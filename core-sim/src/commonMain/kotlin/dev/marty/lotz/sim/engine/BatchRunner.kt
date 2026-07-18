package dev.marty.lotz.sim.engine

import dev.marty.lotz.sim.market.MarketModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.LocalDate
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * A Monte Carlo batch: the same [strategy] run [runs] times with per-run seeds derived from
 * [masterSeed], so the whole batch is reproducible from one number. [concurrency] runs are kept
 * in flight at once via [Dispatchers.Default]; on single-threaded targets (wasmJs) this degrades
 * to cooperative-but-sequential execution rather than true parallelism, which is expected.
 */
data class BatchConfig(
    val strategy: PlayerStrategy,
    val runs: Int,
    val masterSeed: Long,
    val startDate: LocalDate = SimulationEngine.DEFAULT_START_DATE,
    val marketModel: MarketModel = MarketModel(),
    val concurrency: Int = DEFAULT_CONCURRENCY,
    /** Required to be true before [BatchRunner.run] will execute an [StopCondition.UntilJackpot]
     * batch whose estimated total drawings exceeds [BatchRunner.UNTIL_JACKPOT_DRAWING_GUARDRAIL]. */
    val allowExpensiveUntilJackpot: Boolean = false,
) {
    init {
        require(runs >= 1) { "runs must be >= 1" }
        require(concurrency >= 1) { "concurrency must be >= 1" }
    }

    companion object {
        const val DEFAULT_CONCURRENCY = 8
    }
}

data class BatchProgress(val completedRuns: Int, val totalRuns: Int)

data class BatchResult(
    val config: BatchConfig,
    val summaries: List<RunSummary>,
    val stats: BatchStats,
)

/** Thrown when a batch is refused by the until-jackpot guardrail; see [BatchRunner.run]. */
class BatchTooExpensiveException(val estimatedTotalDrawings: Long, message: String) : IllegalArgumentException(message)

object BatchRunner {

    /**
     * Above this many *expected* total drawings across a whole [StopCondition.UntilJackpot] batch,
     * [run] refuses to start unless [BatchConfig.allowExpensiveUntilJackpot] is set. 20,000,000
     * drawings is on the order of a minute of JVM compute for this engine's per-drawing cost; well
     * beyond that (e.g. thousands of real-odds Powerball runs) is a config mistake, not a real
     * batch someone intends to wait for.
     */
    const val UNTIL_JACKPOT_DRAWING_GUARDRAIL = 20_000_000L

    suspend fun run(config: BatchConfig, onProgress: ((BatchProgress) -> Unit)? = null): BatchResult {
        if (config.strategy.stopCondition is StopCondition.UntilJackpot) {
            val estimate = estimateExpectedDrawings(config.strategy, config.runs)
            if (!config.allowExpensiveUntilJackpot && estimate > UNTIL_JACKPOT_DRAWING_GUARDRAIL) {
                throw BatchTooExpensiveException(
                    estimate,
                    "Batch of ${config.runs}x until-jackpot runs of ${config.strategy.game.displayName} " +
                        "is estimated at ~$estimate total drawings, above the " +
                        "$UNTIL_JACKPOT_DRAWING_GUARDRAIL guardrail. Set allowExpensiveUntilJackpot=true to run anyway.",
                )
            }
        }

        val seedRng = Random(config.masterSeed)
        val seeds = LongArray(config.runs) { seedRng.nextLong() }
        val summaries = ArrayList<RunSummary>(config.runs)

        coroutineScope {
            var index = 0
            while (index < config.runs) {
                val chunkEnd = minOf(index + config.concurrency, config.runs)
                val chunkSummaries = (index until chunkEnd)
                    .map { i -> async(Dispatchers.Default) { runOne(config, seeds[i]) } }
                    .awaitAll()
                summaries.addAll(chunkSummaries)
                onProgress?.invoke(BatchProgress(chunkEnd, config.runs))
                currentCoroutineContext().ensureActive()
                index = chunkEnd
            }
        }

        return BatchResult(config, summaries, BatchStats.from(config.strategy, summaries))
    }

    /**
     * Expected total drawings across [runs] independent [StopCondition.UntilJackpot] runs of
     * [strategy], from the geometric-distribution expectation `1 / perDrawingWinProbability` per
     * run. Zero for any other stop condition (those are already bounded).
     */
    fun estimateExpectedDrawings(strategy: PlayerStrategy, runs: Int): Long {
        if (strategy.stopCondition !is StopCondition.UntilJackpot) return 0L
        // With winnings untracked, runs take the AnalyticSimulator path and cost O(1) regardless
        // of expected drawings, so the guardrail does not apply.
        if (!strategy.tracking.trackWinnings) return 0L

        val perEntryOdds = strategy.game.oddsOneIn(strategy.game.jackpotTier)
        val perDrawingWinProbability = (1.0 - (1.0 - 1.0 / perEntryOdds).pow(strategy.entriesPerDrawing))
            .coerceAtLeast(Double.MIN_VALUE)
        val frequencyFactor = when (val frequency = strategy.frequency) {
            PlayFrequency.EveryDrawing -> 1.0
            is PlayFrequency.EveryNthDrawing -> frequency.n.toDouble()
        }
        val expectedDrawingsPerRun = (1.0 / perDrawingWinProbability) * frequencyFactor
        val total = expectedDrawingsPerRun * runs
        return if (total.isFinite() && total < Long.MAX_VALUE.toDouble()) total.roundToLong() else Long.MAX_VALUE
    }

    private suspend fun runOne(config: BatchConfig, seed: Long): RunSummary =
        SimulationEngine.run(config.strategy, seed, config.startDate, config.marketModel).toSummary()
}
