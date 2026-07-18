package dev.marty.lotz.sim.engine

import kotlinx.datetime.LocalDate

/** One (decimated) sample of a run's running totals, for charting. */
data class TimelinePoint(
    val date: LocalDate,
    val drawingIndex: Int,
    val cumulativeSpentCents: Long,
    val cumulativeWonCents: Long,
    val advertisedJackpotCents: Long,
)

/** A notable, human-reportable moment during a run. Kept as a bounded, most-recent window. */
sealed interface SimEvent {
    val date: LocalDate

    data class BigWin(override val date: LocalDate, val tierKey: String, val amountCents: Long) : SimEvent
    data class JackpotSplit(
        override val date: LocalDate,
        val totalWinners: Int,
        val playerShareCents: Long,
    ) : SimEvent
    data class JackpotReset(override val date: LocalDate, val previousAdvertisedJackpotCents: Long) : SimEvent
}

/** Progress callback payload for long-running (e.g. [dev.marty.lotz.sim.engine.StopCondition.UntilJackpot]) runs. */
data class DrawingProgress(
    val drawingsSimulated: Int,
    val currentDate: LocalDate,
    val advertisedJackpotCents: Long,
    val totalSpentCents: Long,
    val totalWonCents: Long,
)

/**
 * The full outcome of one [SimulationEngine.run]. [timeline] and [notableEvents] are bounded
 * regardless of how many drawings were simulated (relevant for [StopCondition.UntilJackpot]).
 */
data class RunResult(
    val strategy: PlayerStrategy,
    val seed: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val drawingsPlayed: Int,
    val totalSpentCents: Long,
    val totalWonCents: Long,
    val tierWinCounts: Map<String, Int>,
    val jackpotWon: Boolean,
    val jackpotAnnuityCents: Long,
    val jackpotCashCents: Long,
    val timeline: List<TimelinePoint>,
    val notableEvents: List<SimEvent>,
) {
    val netCents: Long get() = totalWonCents - totalSpentCents
}

/**
 * A [RunResult] stripped of its timeline/events, for retaining every run of a large [BatchRunner]
 * batch in memory without the per-drawing detail that only matters for single-run inspection.
 */
data class RunSummary(
    val seed: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val drawingsPlayed: Int,
    val totalSpentCents: Long,
    val totalWonCents: Long,
    val tierWinCounts: Map<String, Int>,
    val jackpotWon: Boolean,
    val jackpotAnnuityCents: Long,
    val jackpotCashCents: Long,
) {
    val netCents: Long get() = totalWonCents - totalSpentCents
}

fun RunResult.toSummary(): RunSummary = RunSummary(
    seed = seed,
    startDate = startDate,
    endDate = endDate,
    drawingsPlayed = drawingsPlayed,
    totalSpentCents = totalSpentCents,
    totalWonCents = totalWonCents,
    tierWinCounts = tierWinCounts,
    jackpotWon = jackpotWon,
    jackpotAnnuityCents = jackpotAnnuityCents,
    jackpotCashCents = jackpotCashCents,
)
