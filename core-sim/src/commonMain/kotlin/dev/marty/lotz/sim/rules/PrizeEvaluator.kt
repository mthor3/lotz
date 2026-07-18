package dev.marty.lotz.sim.rules

/** Result of matching one [Ticket] against one [DrawResult]. Jackpot amount is left to the market model. */
data class PrizeResult(
    val tier: PrizeTier?,
    val isJackpotWin: Boolean,
    val amountCents: Long,
)

object PrizeEvaluator {

    fun matchCount(ticket: Ticket, draw: DrawResult): Int = ticket.mainNumbers.count { it in draw.mainNumbers }

    fun bonusMatched(ticket: Ticket, draw: DrawResult): Boolean =
        draw.bonusNumber != null && ticket.bonusNumber == draw.bonusNumber

    /**
     * Evaluates a primary-game ticket. [drawnMultiplier] is the multiplier ball rolled for this
     * drawing (Power Play) when the ticket carries a [GameOption.Multiplier] option id that rolls
     * per-drawing rather than per-ticket (Mega Millions rolls per ticket, via [Ticket.perTicketMultiplier]).
     */
    fun evaluate(
        game: GameDefinition,
        ticket: Ticket,
        draw: DrawResult,
        drawnMultiplier: Int? = null,
    ): PrizeResult {
        val matches = matchCount(ticket, draw)
        val bonusMatch = bonusMatched(ticket, draw)
        val tier = game.tier(matches, bonusMatch) ?: return PrizeResult(null, false, 0)

        if (tier.isJackpot) return PrizeResult(tier, true, 0)

        var amount = tier.baseAmountCents

        val flatMultiplier = ticket.optionIds.firstNotNullOfOrNull { id ->
            game.options.firstOrNull { it.id == id } as? GameOption.FlatMultiplier
        }
        if (flatMultiplier != null) {
            val unlockAmount = flatMultiplier.unlockAmountsCents[tier.key]
            if (unlockAmount != null) {
                amount = unlockAmount
            } else if (tier.key in flatMultiplier.appliesToTierKeys) {
                amount *= flatMultiplier.multiplier
            }
        }

        val multiplierOption = ticket.optionIds.firstNotNullOfOrNull { id ->
            game.options.firstOrNull { it.id == id } as? GameOption.Multiplier
        }
        if (multiplierOption != null) {
            val roll = ticket.perTicketMultiplier ?: drawnMultiplier
            if (roll != null) {
                amount *= roll
                val cap = multiplierOption.tierCaps[tier.key]
                if (cap != null) amount = minOf(amount, cap)
            }
        }

        return PrizeResult(tier, false, amount)
    }
}
