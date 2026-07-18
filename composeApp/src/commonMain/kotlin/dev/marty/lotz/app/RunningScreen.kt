package dev.marty.lotz.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RunningScreen(state: AppUiState, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text("Simulating…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Seed ${state.activeSeed ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val progress = state.progress) {
                is SimulationProgress.Single -> {
                    Text("${formatWholeNumber(progress.progress.drawingsSimulated.toLong())} drawings simulated")
                    Text("Current simulated date: ${progress.progress.currentDate}")
                    Text("Spent ${formatMoney(progress.progress.totalSpentCents)}")
                }
                is SimulationProgress.Batch -> {
                    val fraction = progress.progress.completedRuns.toFloat() / progress.progress.totalRuns
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text("${progress.progress.completedRuns} of ${progress.progress.totalRuns} runs complete")
                }
                null -> Text("Preparing the first drawing…")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}
