package dev.marty.lotz.sim.engine

import dev.marty.lotz.sim.rules.GameDefinition
import kotlinx.datetime.DatePeriod

/** How the player picks numbers for each ticket they buy. */
sealed interface NumberChoice {
    /** Fresh random numbers every drawing (v1 default). */
    data object QuickPick : NumberChoice

    /** The same chosen numbers replayed every drawing. */
    data class Fixed(val mainNumbers: Set<Int>, val bonusNumber: Int? = null) : NumberChoice

    /**
     * Random numbers picked once at run start (from the run's seeded rng), then replayed every
     * drawing like [Fixed]. The engine resolves this to a concrete [Fixed] pick and reports it on
     * [RunResult.fixedNumbers].
     */
    data object RandomOnce : NumberChoice
}

/**
 * What a run measures beyond its stop condition. With [trackWinnings] off the engine skips the
 * market model and prize evaluation entirely and samples outcomes analytically (see
 * [AnalyticSimulator]), making even until-jackpot runs near-instant. [trackSpend] is display-level
 * only: spend is always computed (it is one multiply per drawing, and budget caps need it).
 */
data class TrackingOptions(
    val trackWinnings: Boolean = true,
    val trackSpend: Boolean = true,
)

/** How often the player buys tickets, relative to the game's drawing schedule. */
sealed interface PlayFrequency {
    data object EveryDrawing : PlayFrequency
    data class EveryNthDrawing(val n: Int) : PlayFrequency {
        init {
            require(n >= 1) { "n must be >= 1" }
        }
    }
}

/**
 * When a single run stops. [UntilJackpot] is unbounded in drawing count, so the engine must keep
 * only bounded aggregates/timeline/events for it, never a per-drawing history.
 */
sealed interface StopCondition {
    /** Stop before any purchase that would exceed [totalCents]. Never overspends. */
    data class BudgetCap(val totalCents: Long) : StopCondition {
        init {
            require(totalCents > 0) { "totalCents must be > 0" }
        }
    }

    /** Stop once simulated time reaches [startDate] + [period]. */
    data class Duration(val period: DatePeriod) : StopCondition

    /** Stop the drawing after the player's own ticket hits the jackpot tier. Unbounded in cost/time. */
    data object UntilJackpot : StopCondition
}

/**
 * A player's playstyle for a single run: which game, how many entries, which paid options, how
 * numbers are picked, how often they play, and when the run ends.
 *
 * [reinvestWinnings]: under [StopCondition.BudgetCap], winnings extend the remaining budget only
 * when true; otherwise the cap is against the original stake alone.
 */
data class PlayerStrategy(
    val game: GameDefinition,
    val entriesPerDrawing: Int,
    val optionIds: Set<String> = emptySet(),
    val numberChoice: NumberChoice = NumberChoice.QuickPick,
    val frequency: PlayFrequency = PlayFrequency.EveryDrawing,
    val stopCondition: StopCondition,
    val reinvestWinnings: Boolean = false,
    val tracking: TrackingOptions = TrackingOptions(),
) {
    init {
        require(entriesPerDrawing >= 1) { "entriesPerDrawing must be >= 1" }
        require(!reinvestWinnings || tracking.trackWinnings) {
            "reinvestWinnings requires trackWinnings"
        }
        require(optionIds.all { id -> game.options.any { it.id == id } }) {
            "optionIds must reference options offered by ${game.displayName}"
        }
        val fixed = numberChoice
        if (fixed is NumberChoice.Fixed) {
            require(fixed.mainNumbers.size == game.mainPick) {
                "Fixed main numbers must contain exactly ${game.mainPick} numbers"
            }
            require(fixed.mainNumbers.all { it in 1..game.mainPool }) {
                "Fixed main numbers must be in 1..${game.mainPool}"
            }
            require((fixed.bonusNumber != null) == (game.bonusPool > 0)) {
                "Fixed bonus number must be set iff the game has a bonus pool"
            }
            fixed.bonusNumber?.let {
                require(it in 1..game.bonusPool) { "Fixed bonus number must be in 1..${game.bonusPool}" }
            }
        }
    }

    /** Total cost in cents to buy [entriesPerDrawing] tickets, including all selected options. */
    val costPerDrawingCents: Long
        get() {
            val optionCostPerPlay = optionIds.sumOf { id -> game.options.first { it.id == id }.priceCentsPerPlay }
            return entriesPerDrawing * (game.pricePerPlayCents + optionCostPerPlay)
        }
}
