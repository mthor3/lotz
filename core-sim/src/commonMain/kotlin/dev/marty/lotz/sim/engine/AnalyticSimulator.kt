package dev.marty.lotz.sim.engine

import dev.marty.lotz.sim.market.PoissonSampler
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.expm1
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The fast path for [TrackingOptions.trackWinnings] = false: instead of stepping the
 * [SimulationEngine] loop drawing-by-drawing, samples the run's outcome directly from the game's
 * probabilities — the drawing of the first jackpot hit is geometric, and per-tier match counts
 * given a number of played drawings are Poisson. This makes even real-odds until-jackpot runs
 * (hundreds of millions of expected drawings) effectively instant, at the cost of not modeling
 * winnings or the jackpot market at all: [RunResult.totalWonCents] is 0, timeline/events are
 * empty, and [RunResult.winningsTracked] is false.
 *
 * Deterministic per seed, with a fixed rng consumption order: fixed-pick resolution (RandomOnce
 * only), then the geometric jackpot sample, then tier counts in [GameDefinition.prizeTiers] order.
 * Seeds are NOT interchangeable with the loop path — the same seed produces a different (equally
 * valid) outcome there.
 *
 * Spans can exceed what [LocalDate] supports, so [RunResult.endDate] is clamped to [MAX_DATE] and
 * the true span is reported via [RunResult.simulatedYears].
 */
object AnalyticSimulator {

    /** Cap on sampled played drawings; keeps drawingsPlayed within Int and spend within Long. */
    internal const val MAX_PLAYED_DRAWINGS = 2_000_000_000L

    /** Above this Poisson mean, [sampleCount] switches to a normal approximation, because
     * [PoissonSampler] is O(mean). At mean 1000 the two are statistically indistinguishable. */
    private const val MAX_EXACT_POISSON_MEAN = 1_000.0

    internal val MAX_DATE = LocalDate(9999, 12, 31)

    fun run(
        strategy: PlayerStrategy,
        seed: Long,
        startDate: LocalDate = SimulationEngine.DEFAULT_START_DATE,
        onProgress: ((DrawingProgress) -> Unit)? = null,
    ): RunResult {
        val game = strategy.game
        val rng = Random(seed)

        val numberChoice = SimulationEngine.resolveNumberChoice(strategy, rng)

        val jackpotOdds = game.oddsOneIn(game.jackpotTier)
        val perDrawingJackpotProbability = -expm1(strategy.entriesPerDrawing * ln1p(-1.0 / jackpotOdds))
        val drawingsToJackpot = sampleGeometric(perDrawingJackpotProbability, rng)

        val maxPlayed = maxPlayedDrawings(strategy, startDate)
        val played = if (maxPlayed == null) drawingsToJackpot else minOf(drawingsToJackpot, maxPlayed)
        val jackpotWon = played > 0 && drawingsToJackpot <= played

        val tierWinCounts = mutableMapOf<String, Int>()
        for (tier in game.prizeTiers) {
            if (tier.isJackpot) continue
            val mean = played.toDouble() * strategy.entriesPerDrawing / game.oddsOneIn(tier)
            val count = sampleCount(mean, rng)
            if (count > 0) tierWinCounts[tier.key] = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        if (jackpotWon) tierWinCounts[game.jackpotTier.key] = 1

        val frequencyStride = when (val frequency = strategy.frequency) {
            PlayFrequency.EveryDrawing -> 1L
            is PlayFrequency.EveryNthDrawing -> frequency.n.toLong()
        }
        val firstDrawDate = SimulationEngine.nextDrawDate(startDate, game.drawDays)
        val lastDrawingIndex = if (played == 0L) 0L else (played - 1) * frequencyStride
        val spanDays = drawingIndexToDays(firstDrawDate, game.drawDays, lastDrawingIndex) +
            startDate.daysUntil(firstDrawDate)
        val simulatedYears = spanDays / DAYS_PER_YEAR

        val endDate = when {
            played == 0L -> startDate
            !jackpotWon && strategy.stopCondition is StopCondition.Duration ->
                startDate.plus((strategy.stopCondition as StopCondition.Duration).period)
            spanDays <= startDate.daysUntil(MAX_DATE).toLong() -> startDate.plus(DatePeriod(days = spanDays.toInt()))
            else -> MAX_DATE
        }

        val drawingsPlayed = played.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val totalSpentCents = played * strategy.costPerDrawingCents

        onProgress?.invoke(
            DrawingProgress(
                drawingsSimulated = drawingsPlayed,
                currentDate = endDate,
                advertisedJackpotCents = game.baseJackpotCents,
                totalSpentCents = totalSpentCents,
                totalWonCents = 0L,
            ),
        )

        return RunResult(
            strategy = strategy,
            seed = seed,
            startDate = startDate,
            endDate = endDate,
            drawingsPlayed = drawingsPlayed,
            totalSpentCents = totalSpentCents,
            totalWonCents = 0L,
            tierWinCounts = tierWinCounts,
            jackpotWon = jackpotWon,
            jackpotAnnuityCents = 0L,
            jackpotCashCents = 0L,
            timeline = emptyList(),
            notableEvents = emptyList(),
            winningsTracked = false,
            fixedNumbers = numberChoice as? NumberChoice.Fixed,
            simulatedYears = simulatedYears,
        )
    }

    /** Max drawings the player can buy under the stop condition; null when unbounded. */
    internal fun maxPlayedDrawings(strategy: PlayerStrategy, startDate: LocalDate): Long? {
        val frequencyStride = when (val frequency = strategy.frequency) {
            PlayFrequency.EveryDrawing -> 1L
            is PlayFrequency.EveryNthDrawing -> frequency.n.toLong()
        }
        return when (val stop = strategy.stopCondition) {
            StopCondition.UntilJackpot -> null
            is StopCondition.BudgetCap -> stop.totalCents / strategy.costPerDrawingCents
            is StopCondition.Duration -> {
                val endDate = startDate.plus(stop.period)
                val firstDrawDate = SimulationEngine.nextDrawDate(startDate, strategy.game.drawDays)
                val totalDrawings = countDrawDates(firstDrawDate, endDate, strategy.game.drawDays)
                (totalDrawings + frequencyStride - 1) / frequencyStride
            }
        }
    }

    /** Draw dates in `[firstDrawDate, endExclusive)`, where firstDrawDate is itself a draw date. */
    internal fun countDrawDates(firstDrawDate: LocalDate, endExclusive: LocalDate, drawDays: Set<DayOfWeek>): Long {
        if (firstDrawDate >= endExclusive) return 0L
        val totalDays = firstDrawDate.daysUntil(endExclusive)
        var count = (totalDays / 7).toLong() * drawDays.size
        val remainderStart = firstDrawDate.plus(DatePeriod(days = (totalDays / 7) * 7))
        var date = remainderStart
        while (date < endExclusive) {
            if (date.dayOfWeek in drawDays) count++
            date = date.plus(DatePeriod(days = 1))
        }
        return count
    }

    /** Days from [firstDrawDate] to the draw date [drawingIndex] draw dates after it, without
     * iterating: whole weeks arithmetically, plus a ≤7-day walk for the remainder. */
    internal fun drawingIndexToDays(firstDrawDate: LocalDate, drawDays: Set<DayOfWeek>, drawingIndex: Long): Long {
        if (drawingIndex <= 0L) return 0L
        val perWeek = drawDays.size
        val fullWeeks = drawingIndex / perWeek
        var remainder = (drawingIndex % perWeek).toInt()
        var days = fullWeeks * 7
        var date = firstDrawDate
        while (remainder > 0) {
            date = date.plus(DatePeriod(days = 1))
            days++
            if (date.dayOfWeek in drawDays) remainder--
        }
        return days
    }

    /** Number of the trial (1-based) of the first success in Bernoulli(p) trials. */
    internal fun sampleGeometric(p: Double, rng: Random): Long {
        if (p >= 1.0) return 1L
        require(p > 0.0) { "success probability must be > 0" }
        val u = rng.nextDouble().coerceAtLeast(Double.MIN_VALUE)
        val trials = floor(ln(u) / ln1p(-p)) + 1.0
        return if (trials >= MAX_PLAYED_DRAWINGS.toDouble()) MAX_PLAYED_DRAWINGS else trials.toLong()
    }

    /** Poisson([mean]) sample; exact for small means, normal approximation for large ones. */
    internal fun sampleCount(mean: Double, rng: Random): Long = when {
        mean <= 0.0 -> 0L
        mean <= MAX_EXACT_POISSON_MEAN -> PoissonSampler.sample(mean, rng).toLong()
        else -> (mean + sqrt(mean) * sampleGaussian(rng)).roundToLong().coerceAtLeast(0L)
    }

    /** Standard normal via Box–Muller; always consumes exactly two rng doubles. */
    private fun sampleGaussian(rng: Random): Double {
        val u1 = rng.nextDouble().coerceAtLeast(Double.MIN_VALUE)
        val u2 = rng.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }
}
