package dev.marty.lotz.sim.market

import dev.marty.lotz.sim.rules.GameDefinition
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

data class JackpotState(val advertisedJackpotCents: Long) {
    init {
        require(advertisedJackpotCents >= 0)
    }
}

data class JackpotDrawOutcome(
    val previousAdvertisedJackpotCents: Long,
    val nextState: JackpotState,
    val otherJackpotWinners: Int,
    val playerWon: Boolean,
    val totalJackpotWinners: Int,
    val playerAnnuityShareCents: Long,
    val playerCashShareCents: Long,
    val rolloverContributionCents: Long,
) {
    val didReset: Boolean get() = totalJackpotWinners > 0
}

/**
 * Offline co-player and rolling-jackpot model. See docs/market-model.md for its calibration,
 * source data, boundaries, and intentional simplifications.
 */
class MarketModel(
    private val coefficientsByGameId: Map<String, MarketCoefficients> = MarketCoefficients.launchGames,
) {
    fun coefficientsFor(game: GameDefinition): MarketCoefficients =
        coefficientsByGameId[game.id] ?: error("No market coefficients for game '${game.id}'")

    /** Expected jackpot-bearing co-player plays, before draw-to-draw noise. */
    fun expectedSalesForDrawing(game: GameDefinition, advertisedJackpotCents: Long): Long {
        val coefficients = coefficientsFor(game)
        val jackpot = maxOf(advertisedJackpotCents, coefficients.referenceJackpotCents).toDouble()
        val referenceJackpot = coefficients.referenceJackpotCents.toDouble()
        val threshold = coefficients.frenzyThresholdCents

        val expected = if (threshold == null || jackpot <= threshold.toDouble()) {
            coefficients.referenceSalesTickets * (jackpot / referenceJackpot).pow(coefficients.baseElasticity)
        } else {
            val ticketsAtThreshold = coefficients.referenceSalesTickets *
                (threshold.toDouble() / referenceJackpot).pow(coefficients.baseElasticity)
            ticketsAtThreshold * (jackpot / threshold.toDouble()).pow(coefficients.frenzyElasticity!!)
        }
        return expected.roundToLong().coerceAtLeast(1L)
    }

    /** Expected sales with bounded, multiplicative draw-to-draw noise. Deterministic under [random]. */
    fun salesForDrawing(game: GameDefinition, advertisedJackpotCents: Long, random: Random): Long {
        val coefficients = coefficientsFor(game)
        val shock = 1.0 + (random.nextDouble() * 2.0 - 1.0) * coefficients.salesNoiseFraction
        return (expectedSalesForDrawing(game, advertisedJackpotCents) * shock)
            .roundToLong()
            .coerceAtLeast(1L)
    }

    fun expectedOtherJackpotWinners(game: GameDefinition, salesTickets: Long): Double {
        require(salesTickets >= 0)
        return salesTickets.toDouble() / game.oddsOneIn(game.jackpotTier)
    }

    fun otherJackpotWinners(game: GameDefinition, salesTickets: Long, random: Random): Int =
        PoissonSampler.sample(expectedOtherJackpotWinners(game, salesTickets), random)

    fun cashValueCents(game: GameDefinition, advertisedJackpotCents: Long): Long =
        (advertisedJackpotCents * coefficientsFor(game).cashValueRatio).roundToLong()

    fun rolloverContributionCents(game: GameDefinition, salesTickets: Long): Long {
        require(salesTickets >= 0)
        val coefficients = coefficientsFor(game)
        return (salesTickets.toDouble() * coefficients.ticketPriceCents *
            coefficients.advertisedJackpotContributionRate).roundToLong()
    }

    /**
     * Resolves a drawing. Any jackpot winner resets the next drawing to the game's base jackpot;
     * otherwise base-game sales fund the advertised-annuity rollover. A player co-win is split
     * evenly with every winning co-player play.
     */
    fun advanceJackpot(
        state: JackpotState,
        game: GameDefinition,
        salesTickets: Long,
        otherWinners: Int,
        playerWon: Boolean,
    ): JackpotDrawOutcome {
        require(salesTickets >= 0)
        require(otherWinners >= 0)
        val totalWinners = otherWinners + if (playerWon) 1 else 0

        if (totalWinners > 0) {
            val annuityShare = if (playerWon) state.advertisedJackpotCents / totalWinners else 0L
            return JackpotDrawOutcome(
                previousAdvertisedJackpotCents = state.advertisedJackpotCents,
                nextState = JackpotState(game.baseJackpotCents),
                otherJackpotWinners = otherWinners,
                playerWon = playerWon,
                totalJackpotWinners = totalWinners,
                playerAnnuityShareCents = annuityShare,
                playerCashShareCents = cashValueCents(game, annuityShare),
                rolloverContributionCents = 0L,
            )
        }

        val contribution = rolloverContributionCents(game, salesTickets)
        return JackpotDrawOutcome(
            previousAdvertisedJackpotCents = state.advertisedJackpotCents,
            nextState = JackpotState(state.advertisedJackpotCents + contribution),
            otherJackpotWinners = 0,
            playerWon = false,
            totalJackpotWinners = 0,
            playerAnnuityShareCents = 0L,
            playerCashShareCents = 0L,
            rolloverContributionCents = contribution,
        )
    }
}
