package dev.marty.lotz.sim.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class OddsTest {

    private fun assertOddsWithinTolerance(expected: Double, actual: Double, tolerancePct: Double = 0.01) {
        val relError = kotlin.math.abs(actual - expected) / expected
        assertEquals(true, relError < tolerancePct, "expected ~$expected, got $actual (rel error $relError)")
    }

    @Test
    fun megabucksJackpotOddsMatchPublishedFigure() {
        val odds = Games.megabucks.oddsOneIn(Games.megabucks.jackpotTier)
        // Combinatorial per-play odds: C(48,6) = 12,271,512.
        assertOddsWithinTolerance(12_271_512.0, odds)
    }

    @Test
    fun powerballJackpotOddsMatchPublishedFigure() {
        val odds = Games.powerball.oddsOneIn(Games.powerball.jackpotTier)
        assertOddsWithinTolerance(292_201_338.0, odds)
    }

    @Test
    fun powerballAllTierOddsMatchPublished() {
        val published = mapOf(
            "5+1" to 292_201_338.00,
            "5+0" to 11_688_053.52,
            "4+1" to 913_129.18,
            "4+0" to 36_525.17,
            "3+1" to 14_494.11,
            "3+0" to 579.76,
            "2+1" to 701.33,
            "1+1" to 91.98,
            "0+1" to 38.32,
        )
        for (tier in Games.powerball.prizeTiers) {
            val expected = published.getValue(tier.key)
            val actual = Games.powerball.oddsOneIn(tier)
            assertOddsWithinTolerance(expected, actual, tolerancePct = 0.02)
        }
    }

    @Test
    fun megaMillionsJackpotOddsMatchPublishedFigure() {
        val odds = Games.megaMillions.oddsOneIn(Games.megaMillions.jackpotTier)
        assertOddsWithinTolerance(290_472_336.0, odds)
    }

    @Test
    fun megaMillionsAllTierOddsMatchPublished() {
        val published = mapOf(
            "5+1" to 290_472_336.0,
            "5+0" to 12_629_232.0,
            "4+1" to 893_761.0,
            "4+0" to 38_859.0,
            "3+1" to 13_965.0,
            "3+0" to 607.0,
            "2+1" to 665.0,
            "1+1" to 86.0,
            "0+1" to 35.0,
        )
        for (tier in Games.megaMillions.prizeTiers) {
            val expected = published.getValue(tier.key)
            val actual = Games.megaMillions.oddsOneIn(tier)
            assertOddsWithinTolerance(expected, actual, tolerancePct = 0.02)
        }
    }

    @Test
    fun combinatoricsChooseBasicValues() {
        assertEquals(1L, Combinatorics.choose(5, 0))
        assertEquals(5L, Combinatorics.choose(5, 1))
        assertEquals(10L, Combinatorics.choose(5, 2))
        assertEquals(1L, Combinatorics.choose(5, 5))
        assertEquals(0L, Combinatorics.choose(5, 6))
        assertEquals(13_983_816L, Combinatorics.choose(49, 6))
    }
}
