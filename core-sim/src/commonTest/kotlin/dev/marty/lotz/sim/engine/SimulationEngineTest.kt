package dev.marty.lotz.sim.engine

import dev.marty.lotz.sim.market.MarketCoefficients
import dev.marty.lotz.sim.market.MarketModel
import dev.marty.lotz.sim.rules.GameDefinition
import dev.marty.lotz.sim.rules.Games
import dev.marty.lotz.sim.rules.PrizeTier
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationEngineTest {

    /** Pick-1-of-3, drawn thrice a week: ~1-in-3 jackpot odds so UntilJackpot resolves in a handful of drawings. */
    private val tinyGame = GameDefinition(
        id = "tiny-jackpot-game",
        displayName = "Tiny",
        mainPool = 3,
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

    private val startDate = LocalDate(2026, 1, 1)

    @Test
    fun budgetCapNeverOverspendsAndStopsAtTheBoundary() = runTest {
        val strategy = PlayerStrategy(
            game = Games.powerball,
            entriesPerDrawing = 2,
            stopCondition = StopCondition.BudgetCap(10 * 2 * Games.powerball.pricePerPlayCents),
        )
        val budget = (strategy.stopCondition as StopCondition.BudgetCap).totalCents
        val result = SimulationEngine.run(strategy, seed = 1L, startDate = startDate)

        assertTrue(result.totalSpentCents <= budget)
        if (!result.jackpotWon) {
            // Every drawing bought costs the same fixed amount, so spend is an exact multiple of it.
            assertEquals(0L, result.totalSpentCents % strategy.costPerDrawingCents)
            // The run must have stopped because the *next* purchase would have exceeded the cap.
            val remaining = budget - result.totalSpentCents
            assertTrue(remaining < strategy.costPerDrawingCents)
        }
    }

    @Test
    fun durationStopMatchesTheGamesDrawScheduleWithinTheWindow() = runTest {
        val period = DatePeriod(days = 14)
        val strategy = PlayerStrategy(
            game = Games.powerball,
            entriesPerDrawing = 1,
            stopCondition = StopCondition.Duration(period),
        )
        val result = SimulationEngine.run(strategy, seed = 2L, startDate = startDate)

        val endDate = startDate.plus(period)
        var expected = 0
        var d = startDate
        while (d < endDate) {
            if (d.dayOfWeek in Games.powerball.drawDays) expected++
            d = d.plus(DatePeriod(days = 1))
        }

        assertEquals(expected, result.drawingsPlayed)
        assertTrue(result.timeline.all { it.date < endDate })
    }

    @Test
    fun untilJackpotCompletesQuicklyOnARiggedGame() = runTest {
        val strategy = PlayerStrategy(
            game = tinyGame,
            entriesPerDrawing = 5,
            stopCondition = StopCondition.UntilJackpot,
        )
        val result = SimulationEngine.run(strategy, seed = 3L, startDate = startDate, marketModel = tinyMarket)

        assertTrue(result.jackpotWon)
        assertTrue(result.drawingsPlayed in 1..1000, "expected a quick resolution, got ${result.drawingsPlayed} drawings")
        assertTrue(result.jackpotAnnuityCents > 0)
    }

    @Test
    fun sameSeedProducesIdenticalRunResults() = runTest {
        val strategy = PlayerStrategy(
            game = Games.megabucks,
            entriesPerDrawing = 3,
            optionIds = setOf("kicker"),
            stopCondition = StopCondition.BudgetCap(5_000_00),
        )
        val resultA = SimulationEngine.run(strategy, seed = 42L, startDate = startDate)
        val resultB = SimulationEngine.run(strategy, seed = 42L, startDate = startDate)

        assertEquals(resultA, resultB)
    }

    @Test
    fun netEqualsWonMinusSpentAcrossStrategiesAndGames() = runTest {
        val strategies = listOf(
            PlayerStrategy(Games.powerball, 2, setOf("power-play"), stopCondition = StopCondition.BudgetCap(50_00 * 20)),
            PlayerStrategy(Games.megaMillions, 1, setOf("built-in-multiplier"), stopCondition = StopCondition.BudgetCap(5_00 * 30)),
            PlayerStrategy(Games.megabucks, 4, stopCondition = StopCondition.BudgetCap(50 * 40)),
        )
        for ((index, strategy) in strategies.withIndex()) {
            val result = SimulationEngine.run(strategy, seed = 100L + index, startDate = startDate)
            assertEquals(result.totalWonCents - result.totalSpentCents, result.netCents, strategy.game.displayName)
        }
    }

    @Test
    fun timelineStaysBoundedEvenForALongUntilJackpotRun() = runTest {
        // Long odds (1-in-5,000 per entry) force enough drawings to exercise timeline decimation.
        val stubbornGame = tinyGame.copy(mainPool = 5_000, mainPick = 1)
        val stubbornMarket = MarketModel(
            mapOf(
                stubbornGame.id to MarketCoefficients(
                    gameId = stubbornGame.id,
                    referenceJackpotCents = stubbornGame.baseJackpotCents,
                    referenceSalesTickets = 10,
                    baseElasticity = 0.1,
                    salesNoiseFraction = 0.1,
                    ticketPriceCents = 100,
                    advertisedJackpotContributionRate = 0.5,
                    cashValueRatio = 0.5,
                ),
            ),
        )
        val strategy = PlayerStrategy(
            game = stubbornGame,
            entriesPerDrawing = 1,
            stopCondition = StopCondition.UntilJackpot,
        )
        val result = SimulationEngine.run(strategy, seed = 7L, startDate = startDate, marketModel = stubbornMarket)

        assertTrue(result.jackpotWon)
        assertTrue(result.timeline.size <= 2000, "timeline grew unbounded: ${result.timeline.size} points")
    }

    @Test
    fun printsOneYearPowerballRunSummary() = runTest {
        val strategy = PlayerStrategy(
            game = Games.powerball,
            entriesPerDrawing = 2,
            optionIds = setOf("power-play"),
            stopCondition = StopCondition.Duration(DatePeriod(years = 1)),
        )
        val result = SimulationEngine.run(strategy, seed = 2026L, startDate = startDate)

        println(
            "Powerball, 1 year, 2 entries/drawing + Power Play: " +
                "${result.drawingsPlayed} drawings, spent \$${result.totalSpentCents / 100}, " +
                "won \$${result.totalWonCents / 100}, net \$${result.netCents / 100}, " +
                "jackpotWon=${result.jackpotWon}",
        )
        assertTrue(result.drawingsPlayed > 0)
    }

    @Test
    fun everyNthDrawingSkipsPurchasesButJackpotStillAdvances() = runTest {
        val strategy = PlayerStrategy(
            game = Games.powerball,
            entriesPerDrawing = 1,
            frequency = PlayFrequency.EveryNthDrawing(2),
            stopCondition = StopCondition.BudgetCap(Games.powerball.pricePerPlayCents * 5),
        )
        val result = SimulationEngine.run(strategy, seed = 9L, startDate = startDate)

        // With entries every other drawing, the number of drawings simulated is roughly double drawingsPlayed.
        assertTrue(result.drawingsPlayed * 2 <= result.timeline.size + 1)
        assertEquals(result.drawingsPlayed * Games.powerball.pricePerPlayCents, result.totalSpentCents)
    }

    @Test
    fun randomOnceResolvesToOneValidPickAndIsReproduciblePerSeed() = runTest {
        val strategy = PlayerStrategy(
            game = Games.powerball,
            entriesPerDrawing = 1,
            numberChoice = NumberChoice.RandomOnce,
            stopCondition = StopCondition.Duration(DatePeriod(days = 30)),
        )
        val a = SimulationEngine.run(strategy, seed = 21L, startDate = startDate)
        val b = SimulationEngine.run(strategy, seed = 21L, startDate = startDate)

        val pick = a.fixedNumbers
        assertTrue(pick != null, "RandomOnce must expose the resolved pick")
        assertEquals(Games.powerball.mainPick, pick.mainNumbers.size)
        assertTrue(pick.mainNumbers.all { it in 1..Games.powerball.mainPool })
        assertTrue(pick.bonusNumber!! in 1..Games.powerball.bonusPool)
        assertEquals(pick, b.fixedNumbers)
        assertEquals(a.totalWonCents, b.totalWonCents)
    }

    @Test
    fun quickPickRunsExposeNoFixedNumbers() = runTest {
        val strategy = PlayerStrategy(
            game = Games.powerball,
            entriesPerDrawing = 1,
            stopCondition = StopCondition.Duration(DatePeriod(days = 14)),
        )
        val result = SimulationEngine.run(strategy, seed = 3L, startDate = startDate)
        assertEquals(null, result.fixedNumbers)
    }
}
