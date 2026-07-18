package dev.marty.lotz.app

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.marty.lotz.sim.engine.BatchResult
import dev.marty.lotz.sim.engine.HistogramBucket
import dev.marty.lotz.sim.engine.RunResult
import dev.marty.lotz.sim.engine.SimEvent
import dev.marty.lotz.sim.engine.TimelinePoint
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ResultsScreen(state: AppUiState, onConfigureAnother: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 920.dp).then(Modifier), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        Button(onClick = onConfigureAnother, modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp)) {
            Text("Configure another run")
        }
    }
}

private fun resultTitle(result: SimulationResult): String = when (result) {
    is SimulationResult.Single -> "${result.result.strategy.game.displayName} · ${formatWholeNumber(result.result.drawingsPlayed.toLong())} drawings"
    is SimulationResult.Batch -> "${result.result.config.strategy.game.displayName} · ${formatWholeNumber(result.result.stats.runs.toLong())} runs"
}

@Composable
private fun SingleResults(result: RunResult) {
    if (result.jackpotWon) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Jackpot hit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Annuity ${formatMoney(result.jackpotAnnuityCents)} · Cash option ${formatMoney(result.jackpotCashCents)}")
            }
        }
    }
    MetricGrid(
        listOf(
            "Spent" to formatMoney(result.totalSpentCents),
            "Won" to formatMoney(result.totalWonCents),
            "Net" to formatSignedMoney(result.netCents),
            "Simulated span" to "${result.startDate} → ${result.endDate}",
        ),
    )
    SectionCard("Balance over time") {
        if (result.timeline.isEmpty()) Text("No played drawings were recorded.")
        else TimelineChart(result.timeline)
    }
    SectionCard("Event log") {
        if (result.notableEvents.isEmpty()) {
            Text("No notable events recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            result.notableEvents.asReversed().forEach { event -> EventRow(event) }
        }
    }
}

@Composable
private fun BatchResults(result: BatchResult) {
    val stats = result.stats
    SectionCard("Batch statistics") {
        StatLine("Median net", formatMoney(stats.netCentsDistribution.median))
        StatLine("Mean net", formatMoney(stats.netCentsDistribution.mean.toLong()))
        StatLine("P90 net", formatMoney(stats.netCentsDistribution.p90))
        StatLine("P99 net", formatMoney(stats.netCentsDistribution.p99))
        StatLine("Probability of profit", formatPercent(stats.probabilityOfProfit))
        StatLine("Loss per dollar", formatMoney((stats.expectedLossPerDollar * 100).toLong()))
        StatLine("Jackpot hit rate", formatPercent(stats.jackpotWinFraction))
        Spacer(Modifier.height(8.dp))
        Text("Tier hit rates", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        stats.tierHitRates.entries.sortedBy { it.key }.forEach { entry -> StatLine(entry.key, formatPercent(entry.value)) }
    }
    SectionCard("Net outcome distribution") {
        HistogramChart(stats.netHistogram)
    }
    stats.untilJackpot?.let { until ->
        SectionCard("Until-jackpot distribution") {
            StatLine("Median cost", formatMoney(until.costToJackpotCents.median))
            StatLine("Median years", formatYears(until.yearsToJackpot.median))
            StatLine("P90 years", formatYears(until.yearsToJackpot.p90))
            StatLine("Median drawings", formatWholeNumber(until.drawingsToJackpot.median))
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
        maxLabel = formatMoney(maxValue),
        xLabels = listOf(points.first().date.toString(), points.last().date.toString()),
        lineColor = MaterialTheme.colorScheme.primary,
        zeroColor = MaterialTheme.colorScheme.outlineVariant,
    ) { line, zero -> drawSeries(values, minValue, maxValue, line, zero) }
}

@Composable
private fun HistogramChart(buckets: List<HistogramBucket>) {
    val maxCount = buckets.maxOfOrNull { it.count } ?: 1
    ChartFrame(
        minLabel = formatMoney(buckets.firstOrNull()?.rangeStartCents ?: 0L),
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
            drawRect(barColor, Offset(left, top), androidx.compose.ui.geometry.Size(right - left, size.height - top))
        }
        drawLine(zero, Offset(0f, size.height), Offset(size.width, size.height), 1f)
    }
}

@Composable
private fun ChartFrame(
    minLabel: String,
    maxLabel: String,
    xLabels: List<String>,
    lineColor: Color,
    zeroColor: Color,
    content: androidx.compose.ui.graphics.drawscope.DrawScope.(Color, Color) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.height(190.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(maxLabel, style = MaterialTheme.typography.labelSmall)
            Text("0", style = MaterialTheme.typography.labelSmall)
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

private fun formatSignedMoney(cents: Long): String = if (cents >= 0) "+${formatMoney(cents)}" else formatMoney(cents)
private fun formatPercent(value: Double): String = "${(value * 1000).toInt() / 10.0}%"
private fun formatYears(value: Double): String = "${(value * 10).toInt() / 10.0} years"
