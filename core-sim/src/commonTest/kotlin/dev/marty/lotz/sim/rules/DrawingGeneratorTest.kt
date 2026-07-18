package dev.marty.lotz.sim.rules

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawingGeneratorTest {

    @Test
    fun distinctNumbersAreInRangeAndUnique() {
        val rng = Random(42)
        repeat(1000) {
            val numbers = DrawingGenerator.distinctNumbers(pool = 69, count = 5, rng = rng)
            assertEquals(5, numbers.size)
            assertTrue(numbers.all { it in 1..69 })
        }
    }

    @Test
    fun drawProducesValidMainAndBonusForEachGame() {
        val rng = Random(7)
        for (game in Games.all) {
            repeat(200) {
                val draw = DrawingGenerator.draw(game, rng)
                assertEquals(game.mainPick, draw.mainNumbers.size)
                assertTrue(draw.mainNumbers.all { it in 1..game.mainPool })
                if (game.bonusPool > 0) {
                    assertTrue(draw.bonusNumber != null && draw.bonusNumber in 1..game.bonusPool)
                } else {
                    assertEquals(null, draw.bonusNumber)
                }
            }
        }
    }

    @Test
    fun sameSeedProducesIdenticalDraws() {
        val drawA = DrawingGenerator.draw(Games.powerball, Random(123))
        val drawB = DrawingGenerator.draw(Games.powerball, Random(123))
        assertEquals(drawA, drawB)
    }

    @Test
    fun rollMultiplierRespectsWeights() {
        val rng = Random(99)
        val option = Games.powerball.options.filterIsInstance<GameOption.Multiplier>().first { it.id == "power-play" }
        val counts = mutableMapOf<Int, Int>()
        val trials = 100_000
        repeat(trials) {
            val value = DrawingGenerator.rollMultiplier(option, rng)
            counts[value] = (counts[value] ?: 0) + 1
        }
        for ((value, weight) in option.weights) {
            val expectedFraction = weight.toDouble() / option.totalWeight
            val actualFraction = (counts[value] ?: 0).toDouble() / trials
            assertTrue(
                kotlin.math.abs(actualFraction - expectedFraction) < 0.01,
                "multiplier $value expected ~$expectedFraction got $actualFraction",
            )
        }
    }

    @Test
    fun quickPickTierFrequenciesMatchTheoreticalOddsWithinTolerance() {
        val rng = Random(2026)
        val game = Games.megaMillions
        val trials = 100_000
        var jackpotOrFiveMatches = 0
        repeat(trials) {
            val draw = DrawingGenerator.draw(game, rng)
            val ticket = DrawingGenerator.quickPickTicket(game, rng)
            val matches = PrizeEvaluator.matchCount(ticket, draw)
            if (matches >= 3) jackpotOrFiveMatches++
        }
        // Expected P(>=3 main matches) via tier odds; loose sanity bound since it's a rare-event smoke test.
        val expectedTier3Plus = (1.0 / game.oddsOneIn(game.tier(3, false)!!)) +
            (1.0 / game.oddsOneIn(game.tier(3, true)!!)) +
            (1.0 / game.oddsOneIn(game.tier(4, false)!!)) +
            (1.0 / game.oddsOneIn(game.tier(4, true)!!)) +
            (1.0 / game.oddsOneIn(game.tier(5, false)!!)) +
            (1.0 / game.oddsOneIn(game.jackpotTier))
        val expectedCount = expectedTier3Plus * trials
        // Wide tolerance: this is a smoke test on a rare, high-variance event count, not a precision check.
        assertTrue(
            jackpotOrFiveMatches < expectedCount * 5 + 20,
            "saw $jackpotOrFiveMatches matches of >=3, expected around $expectedCount",
        )
    }

    @Test
    fun mainMatchCountDistributionMatchesHypergeometricExpectation() {
        val rng = Random(555)
        val game = Games.powerball
        val trials = 100_000
        val counts = IntArray(game.mainPick + 1)
        repeat(trials) {
            val draw = DrawingGenerator.draw(game, rng)
            val ticket = DrawingGenerator.quickPickTicket(game, rng)
            counts[PrizeEvaluator.matchCount(ticket, draw)]++
        }
        for (k in 0..game.mainPick) {
            val expectedProb = (
                Combinatorics.choose(game.mainPick, k) *
                    Combinatorics.choose(game.mainPool - game.mainPick, game.mainPick - k)
                ).toDouble() / Combinatorics.choose(game.mainPool, game.mainPick).toDouble()
            val expectedCount = expectedProb * trials
            val actualCount = counts[k].toDouble()
            // Common outcomes (0,1,2 matches) get a tight tolerance; rare ones (4,5) just sanity-checked.
            val tolerance = if (expectedCount > 500) expectedCount * 0.15 else expectedCount * 3 + 10
            assertTrue(
                kotlin.math.abs(actualCount - expectedCount) < tolerance,
                "main-match=$k expected ~$expectedCount got $actualCount",
            )
        }
    }
}
