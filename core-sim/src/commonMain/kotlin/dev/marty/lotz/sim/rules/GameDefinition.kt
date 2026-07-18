package dev.marty.lotz.sim.rules

import kotlinx.datetime.DayOfWeek

/**
 * A lottery's rules as data — see docs/game-rules.md for sources. One shared engine (drawing
 * generation + [PrizeEvaluator]) interprets any [GameDefinition]; adding a game means adding one
 * of these, not new engine code.
 */
data class GameDefinition(
    val id: String,
    val displayName: String,
    val mainPool: Int,
    val mainPick: Int,
    val bonusPool: Int,
    /** Price for [playsPerBasePrice] plays, e.g. Megabucks: 100 cents buys 2 plays. */
    val basePriceCents: Long,
    val playsPerBasePrice: Int = 1,
    val prizeTiers: List<PrizeTier>,
    val drawDays: Set<DayOfWeek>,
    val baseJackpotCents: Long,
    val options: List<GameOption> = emptyList(),
) {
    init {
        require(mainPick in 1..mainPool)
        require(playsPerBasePrice >= 1)
        require(prizeTiers.count { it.isJackpot } == 1) { "exactly one jackpot tier required" }
    }

    val pricePerPlayCents: Long get() = basePriceCents / playsPerBasePrice

    val jackpotTier: PrizeTier get() = prizeTiers.first { it.isJackpot }

    fun tier(mainMatches: Int, bonusMatch: Boolean = false): PrizeTier? =
        prizeTiers.firstOrNull { it.mainMatches == mainMatches && it.bonusMatch == bonusMatch }

    fun oddsOneIn(tier: PrizeTier): Double =
        Combinatorics.oddsOneIn(mainPool, mainPick, tier.mainMatches, bonusPool, tier.bonusMatch)
}
