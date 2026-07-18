package dev.marty.lotz.sim.rules

/** An optional add-on (or built-in behavior) a player can attach when buying a [Ticket]. */
sealed interface GameOption {
    val id: String
    val priceCentsPerPlay: Long

    /**
     * Multiplies non-jackpot prizes by a randomly drawn factor. For Powerball's Power Play the
     * roll happens once per drawing (shared by every Power Play ticket that drawing); for Mega
     * Millions' built-in multiplier the roll happens once per ticket at purchase time. Callers
     * decide when to roll; this type only carries the weighted distribution and per-tier caps.
     *
     * [weights] maps multiplier value -> relative ball count. [tierCaps] maps a [PrizeTier.key] to
     * a flat cap in cents that a multiplied prize may not exceed (e.g. Powerball's Match-5 $2M cap).
     */
    data class Multiplier(
        override val id: String,
        override val priceCentsPerPlay: Long,
        val weights: Map<Int, Int>,
        val tierCaps: Map<String, Long> = emptyMap(),
    ) : GameOption {
        val totalWeight: Int get() = weights.values.sum()
    }

    /** A second, independent drawing using the same numbers and its own prize table. */
    data class SecondDraw(
        override val id: String,
        override val priceCentsPerPlay: Long,
        val prizeTiers: List<PrizeTier>,
    ) : GameOption

    /** Flat per-play multiplier applied to specific tiers (Oregon Megabucks' Kicker). */
    data class FlatMultiplier(
        override val id: String,
        override val priceCentsPerPlay: Long,
        val multiplier: Int,
        val appliesToTierKeys: Set<String>,
        /** Tier key -> fixed cash amount this option unlocks in place of the base game's free-ticket tier. */
        val unlockAmountsCents: Map<String, Long> = emptyMap(),
    ) : GameOption
}
