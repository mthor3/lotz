package dev.marty.lotz.sim.market

import dev.marty.lotz.sim.rules.Games

/**
 * Calibrated market inputs. Every value below is derived and sourced in docs/market-model.md.
 * Sales are jackpot-bearing plays, not dollars and not add-on purchases.
 */
data class MarketCoefficients(
    val gameId: String,
    val referenceJackpotCents: Long,
    val referenceSalesTickets: Long,
    val baseElasticity: Double,
    val frenzyThresholdCents: Long? = null,
    val frenzyElasticity: Double? = null,
    val salesNoiseFraction: Double,
    val ticketPriceCents: Long,
    /** Advertised-annuity jackpot growth per cent of jackpot-bearing base-game sales. */
    val advertisedJackpotContributionRate: Double,
    /** Estimated lump-sum cash value divided by the advertised annuity. */
    val cashValueRatio: Double,
) {
    init {
        require(referenceJackpotCents > 0)
        require(referenceSalesTickets > 0)
        require(baseElasticity >= 0.0)
        require(salesNoiseFraction in 0.0..<1.0)
        require(ticketPriceCents > 0)
        require(advertisedJackpotContributionRate >= 0.0)
        require(cashValueRatio in 0.0..1.0)
        require((frenzyThresholdCents == null) == (frenzyElasticity == null))
        frenzyThresholdCents?.let { require(it >= referenceJackpotCents) }
        frenzyElasticity?.let { require(it >= 0.0) }
    }

    companion object {
        val launchGames: Map<String, MarketCoefficients> = listOf(
            MarketCoefficients(
                gameId = Games.megabucks.id,
                referenceJackpotCents = 1_000_000_00,
                referenceSalesTickets = 197_000,
                baseElasticity = 0.345,
                salesNoiseFraction = 0.20,
                ticketPriceCents = 50,
                advertisedJackpotContributionRate = 0.5821,
                cashValueRatio = 0.50,
            ),
            MarketCoefficients(
                gameId = Games.powerball.id,
                referenceJackpotCents = 20_000_000_00,
                referenceSalesTickets = 7_000_000,
                baseElasticity = 0.231,
                frenzyThresholdCents = 400_000_000_00,
                frenzyElasticity = 1.46,
                salesNoiseFraction = 0.25,
                ticketPriceCents = 200,
                advertisedJackpotContributionRate = 0.755702,
                cashValueRatio = 0.45,
            ),
            MarketCoefficients(
                gameId = Games.megaMillions.id,
                referenceJackpotCents = 50_000_000_00,
                referenceSalesTickets = 4_272_000,
                baseElasticity = 0.284,
                frenzyThresholdCents = 400_000_000_00,
                frenzyElasticity = 1.166,
                salesNoiseFraction = 0.12,
                ticketPriceCents = 500,
                advertisedJackpotContributionRate = 0.614011,
                cashValueRatio = 0.45,
            ),
        ).associateBy { it.gameId }
    }
}
