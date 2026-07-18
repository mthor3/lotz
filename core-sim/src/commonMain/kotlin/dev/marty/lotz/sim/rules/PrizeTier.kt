package dev.marty.lotz.sim.rules

/**
 * One row of a game's (or add-on's) prize table.
 *
 * [bonusMatch] is only meaningful when the owning [GameDefinition.bonusPool] is > 0; ignored otherwise.
 * [baseAmountCents] is the pre-multiplier, pre-cap prize; 0 for [isJackpot] tiers, whose real payout comes
 * from the market model (Chunk 3), not from this table.
 */
data class PrizeTier(
    val key: String,
    val mainMatches: Int,
    val bonusMatch: Boolean = false,
    val isJackpot: Boolean = false,
    val baseAmountCents: Long = 0,
)
