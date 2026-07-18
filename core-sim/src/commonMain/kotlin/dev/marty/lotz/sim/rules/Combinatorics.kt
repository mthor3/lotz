package dev.marty.lotz.sim.rules

/**
 * Exact binomial coefficients and lottery-tier odds, computed from the matrix rather than
 * hardcoded — see docs/game-rules.md, which uses these as test fixtures against published odds.
 */
object Combinatorics {
    fun choose(n: Int, k: Int): Long {
        if (k < 0 || k > n) return 0L
        val kk = minOf(k, n - k)
        var result = 1L
        for (i in 0 until kk) {
            result = result * (n - i) / (i + 1)
        }
        return result
    }

    /**
     * Odds (as "1 in X") of matching exactly [mainMatch] of [mainPick] numbers drawn from [mainPool],
     * and, if [bonusPool] > 0, matching the single bonus ball or not per [bonusMatch].
     */
    fun oddsOneIn(
        mainPool: Int,
        mainPick: Int,
        mainMatch: Int,
        bonusPool: Int = 0,
        bonusMatch: Boolean = false,
    ): Double {
        val mainWays = choose(mainPick, mainMatch) * choose(mainPool - mainPick, mainPick - mainMatch)
        val mainTotal = choose(mainPool, mainPick)
        val bonusWays = if (bonusPool > 0) (if (bonusMatch) 1L else (bonusPool - 1).toLong()) else 1L
        val bonusTotal = if (bonusPool > 0) bonusPool.toLong() else 1L
        val favorable = mainWays * bonusWays
        val total = mainTotal * bonusTotal
        return total.toDouble() / favorable.toDouble()
    }
}
