package dev.marty.lotz.app

import dev.marty.lotz.sim.rules.GameDefinition
import dev.marty.lotz.sim.rules.PrizeTier
import kotlin.math.expm1
import kotlin.math.ln1p
import kotlin.math.roundToLong

fun formatMoney(cents: Long): String {
    val negative = cents < 0
    val absolute = if (cents == Long.MIN_VALUE) Long.MAX_VALUE else if (negative) -cents else cents
    val dollars = absolute / 100
    val grouped = dollars.toString().reversed().chunked(3).joinToString(",").reversed()
    val fraction = (absolute % 100).toString().padStart(2, '0')
    return (if (negative) "−" else "") + "$" + grouped + "." + fraction
}

fun formatWholeNumber(value: Long): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()

fun formatOdds(game: GameDefinition): String =
    "1 in ${formatWholeNumber(game.oddsOneIn(game.jackpotTier).roundToLong())}"

/** Jackpot odds for one drawing after buying [entries] independent entries. */
fun formatOddsForEntries(game: GameDefinition, entries: Int): String {
    require(entries > 0) { "entries must be positive" }
    val singleEntryOdds = game.oddsOneIn(game.jackpotTier)
    val probability = -expm1(entries * ln1p(-1.0 / singleEntryOdds))
    return formatOneIn(probability)
}

fun formatMatrix(game: GameDefinition): String = if (game.bonusPool > 0) {
    "Pick ${game.mainPick} of ${game.mainPool} + 1 of ${game.bonusPool}"
} else {
    "Pick ${game.mainPick} of ${game.mainPool}"
}

fun formatBasePrice(game: GameDefinition): String = if (game.playsPerBasePrice > 1) {
    "${formatMoney(game.basePriceCents)} buys ${game.playsPerBasePrice} plays"
} else {
    "${formatMoney(game.basePriceCents)} per play"
}

fun formatSchedule(game: GameDefinition): String = game.drawDays
    .sortedBy { it.ordinal }
    .joinToString(" / ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }

/** Probability as lay-friendly odds: "1 in 4,200". */
fun formatOneIn(probability: Double): String = when {
    probability <= 0.0 -> "never in this simulation"
    probability >= 1.0 -> "every time"
    else -> {
        val odds = (1.0 / probability).roundToLong()
        "1 in ${if (odds >= 1_000_000L) formatCompactCount(odds) else formatWholeNumber(odds)}"
    }
}

/** A year span at human scale: "3.5 years", "11,000 years", "2.8M years". */
fun formatYearsApprox(years: Double): String = when {
    years >= 1_000_000.0 -> "${formatCompactCount(years.roundToLong())} years"
    years >= 1_000.0 -> "${formatWholeNumber(years.roundToLong())} years"
    years >= 10.0 -> "${years.roundToLong()} years"
    years >= 1.0 -> "${formatDecimal(years)} years"
    else -> "under a year"
}

/** How often an event with [expectedPerYear] occurrences per year is felt to happen. */
fun formatExpectedCadence(expectedPerYear: Double): String = when {
    expectedPerYear >= 104.0 -> "about ${(expectedPerYear / 52.1775).roundToLong()}× a week"
    expectedPerYear >= 2.0 -> "about ${expectedPerYear.roundToLong()}× a year"
    expectedPerYear >= 0.9 -> "about once a year"
    expectedPerYear > 0.0 -> "about once every ${formatYearsApprox(1.0 / expectedPerYear)}"
    else -> "practically never"
}

/** Human label for a prize tier: "Matched 4 of 5 + bonus", "Jackpot (all 5 + bonus)". */
fun formatTierLabel(tier: PrizeTier, game: GameDefinition): String = when {
    tier.isJackpot && tier.bonusMatch -> "Jackpot (all ${game.mainPick} + bonus)"
    tier.isJackpot -> "Jackpot (all ${game.mainPick})"
    tier.bonusMatch -> "Matched ${tier.mainMatches} of ${game.mainPick} + bonus"
    else -> "Matched ${tier.mainMatches} of ${game.mainPick}"
}

fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000_000_000L -> "${formatDecimal(value / 1_000_000_000_000.0)}T"
    value >= 1_000_000_000L -> "${formatDecimal(value / 1_000_000_000.0)}B"
    value >= 1_000_000L -> "${formatDecimal(value / 1_000_000.0)}M"
    value >= 1_000L -> "${formatDecimal(value / 1_000.0)}K"
    else -> value.toString()
}

private fun formatDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return if (tenths % 10L == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}
