package dev.marty.lotz.sim.engine

import dev.marty.lotz.sim.market.MarketCoefficients
import dev.marty.lotz.sim.market.MarketModel
import dev.marty.lotz.sim.rules.GameDefinition
import dev.marty.lotz.sim.rules.Games
import dev.marty.lotz.sim.rules.PrizeTier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class BatchRunnerTest {

    private val startDate = LocalDate(2026, 1, 1)

    private fun powerballStrategy(budgetCents: Long = 20_00 * 10) = PlayerStrategy(
        game = Games.powerball,
        entriesPerDrawing = 2,
        stopCondition = StopCondition.BudgetCap(budgetCents),
    )

    @Test
    fun sameMasterSeedProducesIdenticalBatchResults() = runTest {
        val config = BatchConfig(strategy = powerballStrategy(), runs = 20, masterSeed = 123L, startDate = startDate)
        val resultA = BatchRunner.run(config)
        val resultB = BatchRunner.run(config)

        assertEquals(resultA.summaries, resultB.summaries)
        assertEquals(resultA.stats, resultB.stats)
    }

    @Test
    fun differentRunsGetDifferentDerivedSeedsSoResultsVary() = runTest {
        val config = BatchConfig(strategy = powerballStrategy(), runs = 20, masterSeed = 123L, startDate = startDate)
        val result = BatchRunner.run(config)

        assertEquals(20, result.summaries.map { it.seed }.toSet().size, "expected 20 distinct derived seeds")
    }

    @Test
    fun sequentialAndParallelConcurrencyAgreeForTheSameMasterSeed() = runTest {
        val sequential = BatchRunner.run(
            BatchConfig(strategy = powerballStrategy(), runs = 30, masterSeed = 999L, startDate = startDate, concurrency = 1),
        )
        val parallel = BatchRunner.run(
            BatchConfig(strategy = powerballStrategy(), runs = 30, masterSeed = 999L, startDate = startDate, concurrency = 8),
        )

        // Concurrency affects execution order, not the seed each run gets, so both must agree on
        // the *set* of results even though completion order may differ.
        assertEquals(sequential.summaries.toSet(), parallel.summaries.toSet())
        assertEquals(sequential.stats, parallel.stats)
    }

    @Test
    fun progressCallbackReachesTotalRunsExactlyOnce() = runTest {
        val config = BatchConfig(strategy = powerballStrategy(), runs = 17, masterSeed = 5L, startDate = startDate, concurrency = 4)
        val progressUpdates = mutableListOf<BatchProgress>()

        BatchRunner.run(config) { progressUpdates.add(it) }

        assertTrue(progressUpdates.isNotEmpty())
        assertEquals(17, progressUpdates.last().completedRuns)
        assertTrue(progressUpdates.all { it.totalRuns == 17 })
        assertTrue(progressUpdates.zipWithNext().all { (a, b) -> b.completedRuns > a.completedRuns })
    }

    @Test
    fun batchIsCancellableMidRun() = runTest(timeout = 30.seconds) {
        // Real Powerball odds keep each individual run slow enough that a huge batch is still
        // running when we cancel it.
        val config = BatchConfig(
            strategy = powerballStrategy(budgetCents = 20_00 * 5_000),
            runs = 2_000,
            masterSeed = 1L,
            startDate = startDate,
            concurrency = 2,
        )
        var completedRuns = 0
        val job: Deferred<BatchResult> = async {
            BatchRunner.run(config) { completedRuns = it.completedRuns }
        }
        // Let a handful of chunks complete, then cancel.
        while (completedRuns < 4) kotlinx.coroutines.yield()
        job.cancel()

        assertFailsWith<CancellationException> { job.await() }
        assertTrue(completedRuns < 2_000, "batch should not have finished before cancellation")
    }

    @Test
    fun untilJackpotGuardrailRefusesAnExpensiveRealOddsBatchByDefault() = runTest {
        val strategy = PlayerStrategy(
            game = Games.powerball,
            entriesPerDrawing = 1,
            stopCondition = StopCondition.UntilJackpot,
        )
        val config = BatchConfig(strategy = strategy, runs = 5, masterSeed = 1L, startDate = startDate)

        val exception = assertFailsWith<BatchTooExpensiveException> {
            BatchRunner.run(config)
        }
        assertTrue(exception.estimatedTotalDrawings > BatchRunner.UNTIL_JACKPOT_DRAWING_GUARDRAIL)
    }

    @Test
    fun estimateExpectedDrawingsIsZeroForBoundedStopConditions() {
        assertEquals(0L, BatchRunner.estimateExpectedDrawings(powerballStrategy(), runs = 1_000))
    }

    @Test
    fun statsDistributionMatchesHandComputedFixture() {
        fun summary(net: Long, spent: Long, drawings: Int) = RunSummary(
            seed = 0L,
            startDate = startDate,
            endDate = startDate,
            drawingsPlayed = drawings,
            totalSpentCents = spent,
            totalWonCents = spent + net,
            tierWinCounts = if (net > 0) mapOf("5+0" to 1) else emptyMap(),
            jackpotWon = false,
            jackpotAnnuityCents = 0L,
            jackpotCashCents = 0L,
        )

        // Net outcomes: -100, -50, 0, 50, 1000 (sorted). size=5.
        val summaries = listOf(
            summary(net = 0, spent = 100, drawings = 10),
            summary(net = -100, spent = 100, drawings = 10),
            summary(net = 1000, spent = 100, drawings = 10),
            summary(net = -50, spent = 100, drawings = 10),
            summary(net = 50, spent = 100, drawings = 10),
        )
        val strategy = powerballStrategy()
        val stats = BatchStats.from(strategy, summaries)

        // Nearest-rank percentile index = round(p * (n-1)) on the sorted array
        // [-100, -50, 0, 50, 1000]: p50 -> index 2 -> 0; p90 -> index round(3.6)=4 -> 1000;
        // p99 -> index round(3.96)=4 -> 1000.
        assertEquals(-100L, stats.netCentsDistribution.min)
        assertEquals(0L, stats.netCentsDistribution.median)
        assertEquals(1000L, stats.netCentsDistribution.max)
        assertEquals(1000L, stats.netCentsDistribution.p90)
        assertEquals(1000L, stats.netCentsDistribution.p99)
        assertEquals((-100 + -50 + 0 + 50 + 1000) / 5.0, stats.netCentsDistribution.mean)

        // Profit strictly > 0 for two of five runs (50 and 1000).
        assertEquals(2.0 / 5.0, stats.probabilityOfProfit)

        // totalSpent = 500, totalWon = 500 + sum(net) = 500 + 900 = 1400 -> loss per dollar negative (net profit overall).
        val totalSpent = 5 * 100L
        val totalWon = summaries.sumOf { it.totalWonCents }
        assertEquals((totalSpent - totalWon).toDouble() / totalSpent, stats.expectedLossPerDollar)

        // Two runs won a "5+0" tier once each, across 5 runs * 10 drawings = 50 total drawings.
        assertEquals(2.0 / 50.0, stats.tierHitRates.getValue("5+0"))

        assertEquals(null, stats.untilJackpot)
    }

    /** Pick-1-of-5, drawn thrice a week: ~1-in-5 jackpot odds so UntilJackpot resolves in a handful of drawings. */
    private val tinyGame = GameDefinition(
        id = "tiny-jackpot-game-batch",
        displayName = "Tiny",
        mainPool = 5,
        mainPick = 1,
        bonusPool = 0,
        basePriceCents = 100,
        prizeTiers = listOf(PrizeTier(key = "1", mainMatches = 1, isJackpot = true)),
        drawDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        baseJackpotCents = 100_00,
    )

    private val tinyMarket = MarketModel(
        mapOf(
            tinyGame.id to MarketCoefficients(
                gameId = tinyGame.id,
                referenceJackpotCents = tinyGame.baseJackpotCents,
                referenceSalesTickets = 10,
                baseElasticity = 0.1,
                salesNoiseFraction = 0.1,
                ticketPriceCents = 100,
                advertisedJackpotContributionRate = 0.5,
                cashValueRatio = 0.5,
            ),
        ),
    )

    @Test
    fun untilJackpotBatchPopulatesDrawingsAndYearsToJackpotDistributions() = runTest {
        val strategy = PlayerStrategy(
            game = tinyGame,
            entriesPerDrawing = 1,
            stopCondition = StopCondition.UntilJackpot,
        )
        val config = BatchConfig(
            strategy = strategy,
            runs = 10,
            masterSeed = 42L,
            startDate = startDate,
            marketModel = tinyMarket,
            allowExpensiveUntilJackpot = true,
        )
        val result = BatchRunner.run(config)

        assertTrue(result.summaries.all { it.jackpotWon })
        val untilJackpot = result.stats.untilJackpot
        assertTrue(untilJackpot != null)
        assertTrue(untilJackpot.drawingsToJackpot.min >= 1)
        assertTrue(untilJackpot.yearsToJackpot.min >= 0.0)
        assertEquals(result.stats.spentCentsDistribution, untilJackpot.costToJackpotCents)
    }

    @Test
    fun oneThousandRunOneYearPowerballBatchPrintsStatsQuickly() = runTest(timeout = 30.seconds) {
        val strategy = PlayerStrategy(
            game = Games.powerball,
            entriesPerDrawing = 2,
            stopCondition = StopCondition.Duration(DatePeriod(years = 1)),
        )
        val config = BatchConfig(strategy = strategy, runs = 1_000, masterSeed = 2026L, startDate = startDate)
        val result = BatchRunner.run(config)

        println(
            "1,000x 1yr Powerball batch: median net \$${result.stats.netCentsDistribution.median / 100}, " +
                "p(profit)=${result.stats.probabilityOfProfit}, " +
                "lossPerDollar=${result.stats.expectedLossPerDollar}, " +
                "jackpotWinFraction=${result.stats.jackpotWinFraction}",
        )
        assertEquals(1_000, result.summaries.size)
    }
}
