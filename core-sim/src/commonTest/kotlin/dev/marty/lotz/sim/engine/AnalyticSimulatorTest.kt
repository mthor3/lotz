package dev.marty.lotz.sim.engine

import dev.marty.lotz.sim.rules.Games
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnalyticSimulatorTest {

    private val startDate = LocalDate(2026, 1, 1)
    private val untracked = TrackingOptions(trackWinnings = false)

    private fun untilJackpotStrategy(entries: Int = 1) = PlayerStrategy(
        game = Games.powerball,
        entriesPerDrawing = entries,
        stopCondition = StopCondition.UntilJackpot,
        tracking = untracked,
    )

    @Test
    fun sameSeedReproducesTheSameResult() {
        val strategy = untilJackpotStrategy()
        val a = AnalyticSimulator.run(strategy, seed = 42L, startDate = startDate)
        val b = AnalyticSimulator.run(strategy, seed = 42L, startDate = startDate)
        assertEquals(a, b)
    }

    @Test
    fun untilJackpotAlwaysEndsWithTheJackpotAndExactSpend() {
        val strategy = untilJackpotStrategy(entries = 3)
        val result = AnalyticSimulator.run(strategy, seed = 7L, startDate = startDate)

        assertTrue(result.jackpotWon)
        assertFalse(result.winningsTracked)
        assertEquals(0L, result.totalWonCents)
        assertEquals(result.drawingsPlayed * strategy.costPerDrawingCents, result.totalSpentCents)
        assertEquals(1, result.tierWinCounts[Games.powerball.jackpotTier.key])
        assertTrue(result.timeline.isEmpty())
        assertTrue(result.notableEvents.isEmpty())
        assertNotNull(result.simulatedYears)
    }

    @Test
    fun engineDispatchesToTheAnalyticPathWhenWinningsUntracked() = runTest {
        val strategy = untilJackpotStrategy()
        val viaEngine = SimulationEngine.run(strategy, seed = 42L, startDate = startDate)
        val direct = AnalyticSimulator.run(strategy, seed = 42L, startDate = startDate)
        assertEquals(direct, viaEngine)
    }

    @Test
    fun durationBoundMatchesALoopCountedReferenceWindow() {
        val period = DatePeriod(days = 60)
        val strategy = PlayerStrategy(
            game = Games.powerball,
            entriesPerDrawing = 1,
            stopCondition = StopCondition.Duration(period),
            tracking = untracked,
        )

        var expected = 0L
        var date = startDate
        val endDate = startDate.plus(period)
        while (date < endDate) {
            if (date.dayOfWeek in Games.powerball.drawDays) expected++
            date = date.plus(DatePeriod(days = 1))
        }

        assertEquals(expected, AnalyticSimulator.maxPlayedDrawings(strategy, startDate))
    }

    @Test
    fun everyNthDrawingFrequencyDividesTheDurationBound() {
        val period = DatePeriod(days = 60)
        fun strategy(frequency: PlayFrequency) = PlayerStrategy(
            game = Games.powerball,
            entriesPerDrawing = 1,
            stopCondition = StopCondition.Duration(period),
            frequency = frequency,
            tracking = untracked,
        )
        val every = AnalyticSimulator.maxPlayedDrawings(strategy(PlayFrequency.EveryDrawing), startDate)!!
        val third = AnalyticSimulator.maxPlayedDrawings(strategy(PlayFrequency.EveryNthDrawing(3)), startDate)!!
        assertEquals((every + 2) / 3, third)
    }

    @Test
    fun budgetCapBoundIsBudgetOverCostPerDrawing() {
        val strategy = PlayerStrategy(
            game = Games.powerball,
            entriesPerDrawing = 2,
            stopCondition = StopCondition.BudgetCap(10 * 2 * Games.powerball.pricePerPlayCents),
            tracking = untracked,
        )
        assertEquals(10L, AnalyticSimulator.maxPlayedDrawings(strategy, startDate))
        val result = AnalyticSimulator.run(strategy, seed = 5L, startDate = startDate)
        assertTrue(result.drawingsPlayed <= 10)
        assertTrue(result.totalSpentCents <= (strategy.stopCondition as StopCondition.BudgetCap).totalCents)
    }

    @Test
    fun geometricSamplerMeanMatchesTheExpectation() {
        val rng = Random(99L)
        val p = 0.05
        val samples = 20_000
        var total = 0L
        repeat(samples) { total += AnalyticSimulator.sampleGeometric(p, rng) }
        val mean = total.toDouble() / samples
        assertTrue(abs(mean - 1.0 / p) < 0.5, "geometric mean $mean should be near ${1.0 / p}")
    }

    @Test
    fun largeMeanCountSamplerStaysNearTheMean() {
        val rng = Random(4L)
        val mean = 1_000_000.0
        repeat(20) {
            val sample = AnalyticSimulator.sampleCount(mean, rng)
            // 6 sigma = 6000 for this mean; anything within is a sane normal-approximation draw.
            assertTrue(abs(sample - mean) < 6_000.0, "sample $sample too far from mean $mean")
        }
    }

    @Test
    fun realOddsUntilJackpotBatchRunsInstantlyWithoutTheGuardrail() = runTest {
        val config = BatchConfig(strategy = untilJackpotStrategy(), runs = 500, masterSeed = 11L, startDate = startDate)
        val result = BatchRunner.run(config)

        assertEquals(500, result.stats.jackpotWinners)
        assertFalse(result.stats.winningsTracked)
        assertNotNull(result.stats.untilJackpot)
        // Powerball's expected wait is millions of years; the median of 500 runs must be huge.
        assertTrue(result.stats.untilJackpot!!.yearsToJackpot.median > 10_000.0)
    }

    @Test
    fun untrackedRunsBypassTheExpensiveBatchEstimate() {
        assertEquals(0L, BatchRunner.estimateExpectedDrawings(untilJackpotStrategy(), runs = 1_000))
    }
}
