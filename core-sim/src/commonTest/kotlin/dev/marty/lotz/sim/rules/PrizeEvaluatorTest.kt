package dev.marty.lotz.sim.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrizeEvaluatorTest {

    private val draw = DrawResult(mainNumbers = setOf(1, 2, 3, 4, 5), bonusNumber = 6)

    @Test
    fun powerballTableDrivenTierMatching() {
        val cases = listOf(
            // ticket main matches, ticket bonus, expected tier key, expected jackpot flag
            Triple(setOf(1, 2, 3, 4, 5), 6, "5+1" to true),
            Triple(setOf(1, 2, 3, 4, 5), 7, "5+0" to false),
            Triple(setOf(1, 2, 3, 4, 9), 6, "4+1" to false),
            Triple(setOf(1, 2, 3, 4, 9), 7, "4+0" to false),
            Triple(setOf(1, 2, 3, 9, 10), 6, "3+1" to false),
            Triple(setOf(1, 2, 3, 9, 10), 7, "3+0" to false),
            Triple(setOf(1, 2, 9, 10, 11), 6, "2+1" to false),
            Triple(setOf(1, 9, 10, 11, 12), 6, "1+1" to false),
            Triple(setOf(9, 10, 11, 12, 13), 6, "0+1" to false),
        )
        for ((mainNumbers, bonus, expected) in cases) {
            val ticket = Ticket(mainNumbers, bonus)
            val result = PrizeEvaluator.evaluate(Games.powerball, ticket, draw)
            assertEquals(expected.first, result.tier?.key, "for ticket $mainNumbers+$bonus")
            assertEquals(expected.second, result.isJackpotWin)
        }
    }

    @Test
    fun noMatchingTierReturnsNullTierAndZeroAmount() {
        val ticket = Ticket(setOf(9, 10, 11, 12, 13), 7)
        val result = PrizeEvaluator.evaluate(Games.powerball, ticket, draw)
        assertEquals(null, result.tier)
        assertEquals(0L, result.amountCents)
        assertEquals(false, result.isJackpotWin)
    }

    @Test
    fun jackpotTierReportsNoFixedAmount() {
        val ticket = Ticket(setOf(1, 2, 3, 4, 5), 6)
        val result = PrizeEvaluator.evaluate(Games.powerball, ticket, draw)
        assertTrue(result.isJackpotWin)
        assertEquals(0L, result.amountCents) // real amount comes from the market model (Chunk 3)
    }

    @Test
    fun baseAmountsPaidWithoutOptions() {
        val ticket = Ticket(setOf(1, 2, 3, 4, 9), 7) // 4+0
        val result = PrizeEvaluator.evaluate(Games.powerball, ticket, draw)
        assertEquals(100_00L, result.amountCents)
    }

    @Test
    fun powerPlayMultipliesNonJackpotPrizes() {
        val ticket = Ticket(setOf(1, 2, 3, 4, 9), 7, optionIds = setOf("power-play")) // 4+0, base $100
        val result = PrizeEvaluator.evaluate(Games.powerball, ticket, draw, drawnMultiplier = 3)
        assertEquals(300_00L, result.amountCents)
    }

    @Test
    fun powerPlayCapsMatchFiveAtTwoMillionRegardlessOfMultiplier() {
        val ticket = Ticket(setOf(1, 2, 3, 4, 5), 7, optionIds = setOf("power-play")) // 5+0, base $1,000,000
        for (multiplier in listOf(2, 3, 4, 5, 10)) {
            val result = PrizeEvaluator.evaluate(Games.powerball, ticket, draw, drawnMultiplier = multiplier)
            assertEquals(2_000_000_00L, result.amountCents, "multiplier $multiplier should cap at $2M")
        }
    }

    @Test
    fun powerPlayDoesNotApplyToJackpot() {
        val ticket = Ticket(setOf(1, 2, 3, 4, 5), 6, optionIds = setOf("power-play"))
        val result = PrizeEvaluator.evaluate(Games.powerball, ticket, draw, drawnMultiplier = 10)
        assertTrue(result.isJackpotWin)
        assertEquals(0L, result.amountCents)
    }

    @Test
    fun megaMillionsBuiltInMultiplierAppliesPerTicket() {
        val ticket = Ticket(
            mainNumbers = setOf(1, 2, 3, 4, 9),
            bonusNumber = 6,
            optionIds = setOf("built-in-multiplier"),
            perTicketMultiplier = 5,
        )
        // 4+1 tier, base $20,000
        val result = PrizeEvaluator.evaluate(Games.megaMillions, ticket, draw)
        assertEquals(100_000_00L, result.amountCents)
    }

    @Test
    fun megabucksDollarBuysTwoPlays() {
        assertEquals(50L, Games.megabucks.pricePerPlayCents)
        assertEquals(100L, Games.megabucks.basePriceCents)
    }

    @Test
    fun megabucksMatchThreeWithoutKickerIsFreeTicketValue() {
        val megabucksDraw = DrawResult(mainNumbers = setOf(1, 2, 3, 4, 5, 6))
        val ticket = Ticket(setOf(1, 2, 3, 10, 11, 12))
        val result = PrizeEvaluator.evaluate(Games.megabucks, ticket, megabucksDraw)
        assertEquals("3", result.tier?.key)
        assertEquals(50L, result.amountCents)
    }

    @Test
    fun megabucksKickerUnlocksMatchThreeCashPrize() {
        val megabucksDraw = DrawResult(mainNumbers = setOf(1, 2, 3, 4, 5, 6))
        val ticket = Ticket(setOf(1, 2, 3, 10, 11, 12), optionIds = setOf("kicker"))
        val result = PrizeEvaluator.evaluate(Games.megabucks, ticket, megabucksDraw)
        assertEquals("3", result.tier?.key)
        assertEquals(4_00L, result.amountCents)
    }

    @Test
    fun megabucksKickerQuadruplesMatchFourAndFive() {
        val megabucksDraw = DrawResult(mainNumbers = setOf(1, 2, 3, 4, 5, 6))
        val matchFiveTicket = Ticket(setOf(1, 2, 3, 4, 5, 20), optionIds = setOf("kicker"))
        val result = PrizeEvaluator.evaluate(Games.megabucks, matchFiveTicket, megabucksDraw)
        assertEquals("5", result.tier?.key)
        assertEquals(800_00L * 4, result.amountCents)
    }

    @Test
    fun doublePlayJackpotTierIsFlatTenMillionNotRollingJackpot() {
        val doublePlay = Games.powerball.options.filterIsInstance<GameOption.SecondDraw>()
            .first { it.id == "double-play" }
        val tier = doublePlay.prizeTiers.first { it.key == "5+1" }
        assertEquals(false, tier.isJackpot)
        assertEquals(10_000_000_00L, tier.baseAmountCents)
    }
}
