package dev.marty.lotz.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.marty.lotz.sim.engine.BatchResult
import dev.marty.lotz.sim.engine.HistogramBucket
import dev.marty.lotz.sim.engine.NumberChoice
import dev.marty.lotz.sim.engine.PlayFrequency
import dev.marty.lotz.sim.engine.PlayerStrategy
import dev.marty.lotz.sim.engine.RunResult
import dev.marty.lotz.sim.engine.SimEvent
import dev.marty.lotz.sim.engine.TimelinePoint
import dev.marty.lotz.sim.engine.yearsElapsed
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.roundToLong

@Composable
fun ResultsScreen(state: AppUiState, onConfigureAnother: () -> Unit, onRunAgain: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 920.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Simulation results", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                state.result?.let { resultTitle(it) } ?: "Completed",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.result?.let { result ->
            when (result) {
                is SimulationResult.Single -> SingleResults(result.result)
                is SimulationResult.Batch -> BatchResults(result.result)
            }
        }
        Row(
            Modifier.fillMaxWidth().widthIn(max = 920.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onRunAgain, modifier = Modifier.weight(1f)) {
                Text("Run again")
            }
            OutlinedButton(onClick = onConfigureAnother, modifier = Modifier.weight(1f)) {
                Text("Configure another run")
            }
        }
        Text(
            "Run again repeats these settings — with a fresh outcome unless a seed is set.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun resultTitle(result: SimulationResult): String = when (result) {
    is SimulationResult.Single -> "${result.result.strategy.game.displayName} · ${formatWholeNumber(result.result.drawingsPlayed.toLong())} drawings"
    is SimulationResult.Batch -> "${result.result.config.strategy.game.displayName} · ${formatWholeNumber(result.result.stats.runs.toLong())} runs"
}

@Composable
private fun SingleResults(result: RunResult) {
    val strategy = result.strategy
    val trackSpend = strategy.tracking.trackSpend

    if (result.jackpotWon) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Jackpot hit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (result.winningsTracked) {
                    Text("Annuity ${formatMoney(result.jackpotAnnuityCents)} · Cash option ${formatMoney(result.jackpotCashCents)}")
                } else {
                    Text(
                        "After ${formatWholeNumber(result.drawingsPlayed.toLong())} played drawings — " +
                            "about ${formatYearsApprox(result.yearsElapsed)} of playing",
                    )
                }
            }
        }
    }

    result.fixedNumbers?.let { FixedNumbersLine(it) }

    val metrics = buildList {
        if (trackSpend) add("Spent" to formatMoney(result.totalSpentCents))
        if (result.winningsTracked) {
            add("Won" to formatMoney(result.totalWonCents))
            add("Net" to formatSignedMoney(result.netCents))
            add("Simulated span" to "${result.startDate} → ${result.endDate}")
        } else {
            add("Drawings played" to formatWholeNumber(result.drawingsPlayed.toLong()))
            add("Time played" to formatYearsApprox(result.yearsElapsed))
        }
    }
    MetricGrid(metrics)

    TierCountsCard(
        strategy = strategy,
        tierCounts = result.tierWinCounts.mapValues { it.value.toLong() },
        totalDrawingsPlayed = result.drawingsPlayed.toLong(),
        runs = 1,
    )

    if (result.winningsTracked) {
        SectionCard("Jackpot over time") {
            if (result.timeline.isEmpty()) Text("No drawings were recorded.")
            else JackpotChart(result.timeline)
        }
        SectionCard("Balance over time") {
            if (result.timeline.isEmpty()) Text("No played drawings were recorded.")
            else TimelineChart(result.timeline)
        }
        CollapsibleEventLog(result.notableEvents)
    }
}

@Composable
private fun BatchResults(result: BatchResult) {
    val stats = result.stats
    val strategy = result.config.strategy
    val trackSpend = strategy.tracking.trackSpend

    SectionCard("The jackpot") {
        WaffleChart(winners = stats.jackpotWinners, total = stats.runs)
    }

    TierCountsCard(
        strategy = strategy,
        tierCounts = stats.tierTotalCounts,
        totalDrawingsPlayed = stats.totalDrawingsPlayed,
        runs = stats.runs,
    )

    if (stats.winningsTracked) {
        SectionCard("The money") {
            Sentence(
                "A typical player spent ${formatMoney(stats.spentCentsDistribution.median)} and ended " +
                    "${formatSignedMoney(stats.netCentsDistribution.median)} overall.",
            )
            Sentence("Chance of coming out ahead: ${formatOneIn(stats.probabilityOfProfit)}.")
            Sentence(
                "Best simulated outcome: ${formatSignedMoney(stats.netCentsDistribution.max)} · " +
                    "worst: ${formatSignedMoney(stats.netCentsDistribution.min)}.",
            )
            HistogramChart(stats.netHistogram)
        }
    }

    stats.untilJackpot?.let { until ->
        SectionCard("How long until the jackpot?") {
            val years = result.summaries.map { it.yearsElapsed }
            YearsHistogram(years)
            Sentence(
                "Half of the ${formatWholeNumber(stats.runs.toLong())} simulated players waited more than " +
                    "${formatYearsApprox(until.yearsToJackpot.median)} to win.",
            )
            Sentence("For scale: a human lifetime is about 80 years, and all of recorded history spans about 5,000.")
            if (trackSpend) {
                Sentence("The median player spent ${formatMoney(until.costToJackpotCents.median)} getting there.")
            }
        }
    }
}

/** Plain-language line inside a SectionCard, in place of label/value stat rows. */
@Composable
private fun Sentence(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun FixedNumbersLine(pick: NumberChoice.Fixed) {
    Card(Modifier.fillMaxWidth().widthIn(max = 920.dp)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your numbers", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                pick.mainNumbers.sorted().joinToString(" · ") +
                    (pick.bonusNumber?.let { "  +  $it" } ?: ""),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Match counts per prize tier as counts and cadence — never percentages, which either truncate to
 * 0.0% for rare tiers or read as nonsense above 100% for common ones.
 */
@Composable
private fun TierCountsCard(
    strategy: PlayerStrategy,
    tierCounts: Map<String, Long>,
    totalDrawingsPlayed: Long,
    runs: Int,
) {
    val game = strategy.game
    val drawsPerYear = playedDrawingsPerYear(strategy)
    val maxCount = tierCounts.values.maxOrNull() ?: 0L
    SectionCard(if (runs > 1) "What was matched, across all players" else "What was matched") {
        game.prizeTiers.forEach { tier ->
            val count = tierCounts[tier.key] ?: 0L
            val expectedPerYear = drawsPerYear * strategy.entriesPerDrawing / game.oddsOneIn(tier)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formatTierLabel(tier, game),
                        fontWeight = if (tier.isJackpot) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                        "${formatWholeNumber(count)}×",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = if (tier.isJackpot && count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                CountBar(count = count, maxCount = maxCount)
                Text(
                    "1 in ${formatWholeNumber(game.oddsOneIn(tier).roundToLong())} per entry · ${formatExpectedCadence(expectedPerYear)} at this pace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Log-scaled magnitude bar: tier counts span many orders of magnitude, so a linear bar would
 * render everything but the most common tier invisible. */
@Composable
private fun CountBar(count: Long, maxCount: Long) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        drawRoundRect(trackColor, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f))
        if (count > 0 && maxCount > 0) {
            val fraction = (log10(count.toDouble() + 1.0) / log10(maxCount.toDouble() + 1.0)).toFloat().coerceIn(0.02f, 1f)
            drawRoundRect(
                barColor,
                size = Size(size.width * fraction, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f),
            )
        }
    }
}

/** One dot per simulated player (grouped when there are more players than cells); jackpot winners lit. */
@Composable
private fun WaffleChart(winners: Int, total: Int) {
    val cells = minOf(total, 500)
    val playersPerCell = ceil(total.toDouble() / cells).toInt()
    val winnerCells = if (winners == 0) 0 else maxOf(1, (winners.toDouble() * cells / total).roundToLong().toInt())
    val perRow = 25
    val rows = ceil(cells.toDouble() / perRow).toInt()
    val winnerColor = MaterialTheme.colorScheme.primary
    val otherColor = MaterialTheme.colorScheme.surfaceVariant

    Text(
        when (winners) {
            0 -> "None of the ${formatWholeNumber(total.toLong())} simulated players ever hit the jackpot."
            total -> "All ${formatWholeNumber(total.toLong())} simulated players eventually hit the jackpot — see how long it took below."
            else -> "${formatWholeNumber(winners.toLong())} of the ${formatWholeNumber(total.toLong())} simulated players hit the jackpot."
        },
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
    )
    Canvas(Modifier.fillMaxWidth().height((rows * 14).dp)) {
        val cellSize = size.width / perRow
        val radius = (cellSize * 0.32f).coerceAtMost(5.5f * density)
        for (cell in 0 until cells) {
            val row = cell / perRow
            val col = cell % perRow
            drawCircle(
                color = if (cell < winnerCells) winnerColor else otherColor,
                radius = radius,
                center = Offset(col * cellSize + cellSize / 2f, row * 14f * density + 7f * density),
            )
        }
    }
    if (playersPerCell > 1) {
        Text(
            "Each dot ≈ $playersPerCell players.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Distribution of years-to-jackpot across a batch, bucketed linearly. */
@Composable
private fun YearsHistogram(years: List<Double>) {
    if (years.isEmpty()) return
    val minYears = years.min()
    val maxYears = years.max()
    val bucketCount = 20
    val span = (maxYears - minYears).coerceAtLeast(1e-9)
    val counts = IntArray(bucketCount)
    years.forEach { value ->
        val index = (((value - minYears) / span) * bucketCount).toInt().coerceIn(0, bucketCount - 1)
        counts[index]++
    }
    val maxCount = counts.max()
    ChartFrame(
        minLabel = "0",
        midLabel = "",
        maxLabel = "${formatWholeNumber(maxCount.toLong())} players",
        xLabels = listOf(formatYearsApprox(minYears), formatYearsApprox(maxYears)),
        lineColor = MaterialTheme.colorScheme.primary,
        zeroColor = MaterialTheme.colorScheme.outlineVariant,
    ) { barColor, zero ->
        counts.forEachIndexed { index, count ->
            val left = index * size.width / bucketCount + 1f
            val right = (index + 1) * size.width / bucketCount - 1f
            val top = size.height * (1f - count.toFloat() / maxCount)
            drawRect(barColor, Offset(left, top), Size(right - left, size.height - top))
        }
        drawLine(zero, Offset(0f, size.height), Offset(size.width, size.height), 1f)
        // A human-lifetime reference, drawable only when the axis is at human scale.
        val lifetimeFraction = ((80.0 - minYears) / span).toFloat()
        if (lifetimeFraction in 0f..1f && maxYears > 160.0) {
            drawLine(zero, Offset(size.width * lifetimeFraction, 0f), Offset(size.width * lifetimeFraction, size.height), 2f)
        }
    }
}

@Composable
private fun CollapsibleEventLog(events: List<SimEvent>) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().widthIn(max = 920.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Event log (${events.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (expanded) "Hide" else "Show",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (expanded) {
                if (events.isEmpty()) {
                    Text("No notable events recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    events.asReversed().forEach { event -> EventRow(event) }
                }
            }
        }
    }
}

@Composable
private fun MetricGrid(metrics: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth().widthIn(max = 920.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.take(3).forEach { (label, value) ->
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    metrics.drop(3).forEach { (label, value) ->
        Card(Modifier.fillMaxWidth().widthIn(max = 920.dp)) { StatLine(label, value, Modifier.padding(14.dp)) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().widthIn(max = 920.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TimelineChart(points: List<TimelinePoint>) {
    val values = points.map { it.cumulativeWonCents - it.cumulativeSpentCents }
    val minValue = minOf(0L, values.minOrNull() ?: 0L)
    val maxValue = maxOf(0L, values.maxOrNull() ?: 0L)
    ChartFrame(
        minLabel = formatMoney(minValue),
        midLabel = "0",
        maxLabel = formatMoney(maxValue),
        xLabels = listOf(points.first().date.toString(), points.last().date.toString()),
        lineColor = MaterialTheme.colorScheme.primary,
        zeroColor = MaterialTheme.colorScheme.outlineVariant,
    ) { line, zero -> drawSeries(values, minValue, maxValue, line, zero) }
}

/** The advertised jackpot over the run, horizontally scrollable with dates along the x-axis. */
@Composable
private fun JackpotChart(points: List<TimelinePoint>) {
    val values = points.map { it.advertisedJackpotCents }
    val maxValue = values.max()
    val chartWidth = maxOf(560, points.size * 3).dp
    val lineColor = MaterialTheme.colorScheme.primary
    val zeroColor = MaterialTheme.colorScheme.outlineVariant

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.height(190.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(formatMoney(maxValue), style = MaterialTheme.typography.labelSmall)
            Text(formatMoney(0), style = MaterialTheme.typography.labelSmall)
        }
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(chartWidth)) {
                Canvas(Modifier.width(chartWidth).height(190.dp)) {
                    drawSeries(values, 0L, maxValue, lineColor, zeroColor)
                }
                Row(Modifier.width(chartWidth), horizontalArrangement = Arrangement.SpaceBetween) {
                    dateTickLabels(points).forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
    Text(
        "Scroll sideways for the full run. The line resets each time someone wins.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** ~6 evenly spaced dates across the timeline for the x-axis legend. */
private fun dateTickLabels(points: List<TimelinePoint>, ticks: Int = 6): List<String> {
    if (points.size <= ticks) return points.map { it.date.toString() }
    return (0 until ticks).map { tick ->
        points[(tick.toDouble() / (ticks - 1) * (points.size - 1)).toInt()].date.toString()
    }
}

@Composable
private fun HistogramChart(buckets: List<HistogramBucket>) {
    val maxCount = buckets.maxOfOrNull { it.count } ?: 1
    ChartFrame(
        minLabel = formatMoney(buckets.firstOrNull()?.rangeStartCents ?: 0L),
        midLabel = "0",
        maxLabel = formatMoney(buckets.lastOrNull()?.rangeEndCentsExclusive ?: 0L),
        xLabels = listOf("loss", "profit"),
        lineColor = MaterialTheme.colorScheme.primary,
        zeroColor = MaterialTheme.colorScheme.outlineVariant,
    ) { barColor, zero ->
        val gap = size.width / (buckets.size * 1.15f)
        buckets.forEachIndexed { index, bucket ->
            val left = index * size.width / buckets.size + gap * .075f
            val right = (index + 1) * size.width / buckets.size - gap * .075f
            val top = size.height * (1f - bucket.count.toFloat() / maxCount)
            drawRect(barColor, Offset(left, top), Size(right - left, size.height - top))
        }
        drawLine(zero, Offset(0f, size.height), Offset(size.width, size.height), 1f)
    }
}

@Composable
private fun ChartFrame(
    minLabel: String,
    midLabel: String,
    maxLabel: String,
    xLabels: List<String>,
    lineColor: Color,
    zeroColor: Color,
    content: androidx.compose.ui.graphics.drawscope.DrawScope.(Color, Color) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.height(190.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(maxLabel, style = MaterialTheme.typography.labelSmall)
            Text(midLabel, style = MaterialTheme.typography.labelSmall)
            Text(minLabel, style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(190.dp)) { content(lineColor, zeroColor) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                xLabels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    values: List<Long>, minValue: Long, maxValue: Long, lineColor: Color, zeroColor: Color,
) {
    if (values.isEmpty()) return
    val range = (maxValue - minValue).coerceAtLeast(1L).toDouble()
    fun point(index: Int): Offset {
        val x = if (values.size == 1) size.width / 2f else index * size.width / (values.size - 1).toFloat()
        val y = size.height - ((values[index] - minValue) / range * size.height).toFloat()
        return Offset(x, y)
    }
    val zeroY = size.height - ((0L - minValue) / range * size.height).toFloat()
    drawLine(zeroColor, Offset(0f, zeroY), Offset(size.width, zeroY), 1f)
    values.zipWithNext().forEachIndexed { index, _ -> drawLine(lineColor, point(index), point(index + 1), 3f, StrokeCap.Round) }
    drawCircle(lineColor, 4f, point(values.lastIndex))
}

@Composable
private fun EventRow(event: SimEvent) {
    val text = when (event) {
        is SimEvent.BigWin -> "${event.date} · ${event.tierKey} win · ${formatMoney(event.amountCents)}"
        is SimEvent.JackpotSplit -> "${event.date} · Jackpot split ${event.totalWinners}-ways · ${formatMoney(event.playerShareCents)} share"
        is SimEvent.JackpotReset -> "${event.date} · Jackpot reset from ${formatMoney(event.previousAdvertisedJackpotCents)}"
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

private fun playedDrawingsPerYear(strategy: PlayerStrategy): Double {
    val stride = when (val frequency = strategy.frequency) {
        PlayFrequency.EveryDrawing -> 1.0
        is PlayFrequency.EveryNthDrawing -> frequency.n.toDouble()
    }
    return strategy.game.drawDays.size * (365.2425 / 7.0) / stride
}

private fun formatSignedMoney(cents: Long): String = if (cents >= 0) "+${formatMoney(cents)}" else formatMoney(cents)
