package dev.marty.lotz.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ResultsPlaceholderScreen(state: AppUiState, onConfigureAnother: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Simulation complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Results are intentionally compact in Chunk 6. Charts and detailed statistics arrive in Chunk 7.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.result?.let { result ->
                when (result) {
                    is SimulationResult.Single -> SinglePlaceholder(result.result)
                    is SimulationResult.Batch -> BatchPlaceholder(result.result)
                }
            }
            Button(onClick = onConfigureAnother, modifier = Modifier.fillMaxWidth()) {
                Text("Configure another run")
            }
        }
    }
}

@Composable
private fun SinglePlaceholder(result: dev.marty.lotz.sim.engine.RunResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Raw single-run totals", style = MaterialTheme.typography.titleMedium)
            RawLine("Game", result.strategy.game.displayName)
            RawLine("Seed", result.seed.toString())
            RawLine("Drawings played", formatWholeNumber(result.drawingsPlayed.toLong()))
            RawLine("Simulated span", "${result.startDate} → ${result.endDate}")
            RawLine("Spent", formatMoney(result.totalSpentCents))
            RawLine("Won", formatMoney(result.totalWonCents))
            RawLine("Net", formatMoney(result.netCents))
            RawLine("Jackpot won", if (result.jackpotWon) "Yes" else "No")
            RawLine("Tier counts", result.tierWinCounts.toString())
        }
    }
}

@Composable
private fun BatchPlaceholder(result: dev.marty.lotz.sim.engine.BatchResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Raw batch totals", style = MaterialTheme.typography.titleMedium)
            RawLine("Game", result.config.strategy.game.displayName)
            RawLine("Master seed", result.config.masterSeed.toString())
            RawLine("Runs", result.stats.runs.toString())
            RawLine("Median net", formatMoney(result.stats.netCentsDistribution.median))
            RawLine("Mean net", formatMoney(result.stats.netCentsDistribution.mean.toLong()))
            RawLine("Profit probability", "${(result.stats.probabilityOfProfit * 1000).toInt() / 10.0}%")
            RawLine("Expected loss / $1", formatMoney((result.stats.expectedLossPerDollar * 100).toLong()))
            RawLine("Jackpot fraction", result.stats.jackpotWinFraction.toString())
        }
    }
}

@Composable
private fun RawLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}
