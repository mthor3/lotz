package dev.marty.lotz.app

import dev.marty.lotz.sim.rules.GameDefinition
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

fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000_000L -> "${formatDecimal(value / 1_000_000_000.0)}B"
    value >= 1_000_000L -> "${formatDecimal(value / 1_000_000.0)}M"
    value >= 1_000L -> "${formatDecimal(value / 1_000.0)}K"
    else -> value.toString()
}

private fun formatDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return if (tenths % 10L == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}
