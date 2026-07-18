package dev.marty.lotz.sim.market

import dev.marty.lotz.sim.rules.Games
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketModelTest {
    private val model = MarketModel()

    private fun assertRelative(expected: Double, actual: Double, tolerance: Double) {
        val error = abs(actual - expected) / expected
        assertTrue(error <= tolerance, "expected ~$expected, got $actual (relative error $error)")
    }

    @Test
    fun expectedSalesAreMonotonicInJackpotForEveryGame() {
        for (game in Games.all) {
            val jackpots = listOf(1L, 2L, 5L, 10L, 20L, 50L, 100L, 400L, 1_000L)
                .map { it * 1_000_000_00 }
                .filter { it >= game.baseJackpotCents }
            val sales = jackpots.map { model.expectedSalesForDrawing(game, it) }
            assertEquals(sales.sorted(), sales, game.displayName)
            assertEquals(sales.distinct().size, sales.size, game.displayName)
        }
    }

    @Test
    fun stochasticSalesAreSeededAndStayInsideDocumentedNoiseBand() {
        for (game in Games.all) {
            val jackpot = game.baseJackpotCents * 10
            val expected = model.expectedSalesForDrawing(game, jackpot)
            val coefficients = model.coefficientsFor(game)
            val first = model.salesForDrawing(game, jackpot, Random(8675309))
            val replay = model.salesForDrawing(game, jackpot, Random(8675309))
            assertEquals(first, replay)
            assertTrue(first >= expected * (1.0 - coefficients.salesNoiseFraction) - 1.0)
            assertTrue(first <= expected * (1.0 + coefficients.salesNoiseFraction) + 1.0)
        }
    }

    @Test
    fun calibrationPointsReproducePublishedSales() {
        // Megabucks inferred jackpot-bearing plays from exact-match-3 winner counts.
        assertRelative(249_011.0, model.expectedSalesForDrawing(Games.megabucks, 2_000_000_00).toDouble(), 0.01)

        // Powerball official worksheet: representative $28M gross near $400M and $107.4M at $1B.
        val pbAt400Gross = model.expectedSalesForDrawing(Games.powerball, 400_000_000_00) * 2.0
        val pbAt1bGross = model.expectedSalesForDrawing(Games.powerball, 1_000_000_000_00) * 2.0
        assertRelative(28_000_000.0, pbAt400Gross, 0.01)
        assertRelative(107_437_668.0, pbAt1bGross, 0.02)

        // Mega Millions official worksheet base-game sales at $50M, $400M, and $980M.
        val mmAt50Gross = model.expectedSalesForDrawing(Games.megaMillions, 50_000_000_00) * 5.0
        val mmAt400Gross = model.expectedSalesForDrawing(Games.megaMillions, 400_000_000_00) * 5.0
        val mmAt980Gross = model.expectedSalesForDrawing(Games.megaMillions, 980_000_000_00) * 5.0
        assertRelative(21_362_490.0, mmAt50Gross, 0.01)
        assertRelative(38_515_850.0, mmAt400Gross, 0.01)
        assertRelative(109_762_000.0, mmAt980Gross, 0.01)
    }

    @Test
    fun poissonSampleMeanMatchesItsExpectation() {
        val expectedMean = 0.35
        val random = Random(20260717)
        val samples = 200_000
        var total = 0L
        repeat(samples) { total += PoissonSampler.sample(expectedMean, random) }
        assertRelative(expectedMean, total.toDouble() / samples, 0.015)
    }

    @Test
    fun splitAndResetIncludePlayerAndEveryOtherWinner() {
        val outcome = model.advanceJackpot(
            state = JackpotState(600_000_000_00),
            game = Games.powerball,
            salesTickets = 20_000_000,
            otherWinners = 2,
            playerWon = true,
        )

        assertTrue(outcome.didReset)
        assertEquals(3, outcome.totalJackpotWinners)
        assertEquals(200_000_000_00, outcome.playerAnnuityShareCents)
        assertEquals(90_000_000_00, outcome.playerCashShareCents)
        assertEquals(Games.powerball.baseJackpotCents, outcome.nextState.advertisedJackpotCents)
        assertEquals(0L, outcome.rolloverContributionCents)
    }

    @Test
    fun anotherWinnerResetsWithoutPayingThePlayer() {
        val outcome = model.advanceJackpot(
            state = JackpotState(80_000_000_00),
            game = Games.megaMillions,
            salesTickets = 5_000_000,
            otherWinners = 1,
            playerWon = false,
        )
        assertTrue(outcome.didReset)
        assertEquals(0L, outcome.playerAnnuityShareCents)
        assertEquals(Games.megaMillions.baseJackpotCents, outcome.nextState.advertisedJackpotCents)
    }

    @Test
    fun noWinnerRollsSalesContributionIntoAdvertisedJackpot() {
        val salesTickets = 7_000_000L
        val contribution = model.rolloverContributionCents(Games.powerball, salesTickets)
        val outcome = model.advanceJackpot(
            state = JackpotState(Games.powerball.baseJackpotCents),
            game = Games.powerball,
            salesTickets = salesTickets,
            otherWinners = 0,
            playerWon = false,
        )

        assertEquals(1_057_982_800L, contribution)
        assertEquals(contribution, outcome.rolloverContributionCents)
        assertEquals(Games.powerball.baseJackpotCents + contribution, outcome.nextState.advertisedJackpotCents)
    }

    @Test
    fun powerballMedianResetTimeIsARealisticRolloverRun() {
        val resetDrawings = ArrayList<Int>()
        repeat(4_000) { runIndex ->
            val random = Random(100_000 + runIndex)
            var state = JackpotState(Games.powerball.baseJackpotCents)
            for (drawing in 1..100) {
                val sales = model.salesForDrawing(Games.powerball, state.advertisedJackpotCents, random)
                val winners = model.otherJackpotWinners(Games.powerball, sales, random)
                val outcome = model.advanceJackpot(state, Games.powerball, sales, winners, playerWon = false)
                if (outcome.didReset) {
                    resetDrawings += drawing
                    break
                }
                state = outcome.nextState
            }
        }

        assertEquals(4_000, resetDrawings.size)
        resetDrawings.sort()
        val median = resetDrawings[resetDrawings.size / 2]
        // Powerball describes sales as normally producing roughly month-to-six-week runs.
        assertTrue(median in 12..24, "median reset was $median drawings")
    }
}
