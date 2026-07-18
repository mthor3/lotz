package dev.marty.lotz.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val LotzLightColors = lightColorScheme(
    primary = Color(0xFF176B4D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F1D5),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4D6358),
    surface = Color(0xFFF8FAF7),
    surfaceVariant = Color(0xFFDDE5DF),
)

private val LotzDarkColors = darkColorScheme(
    primary = Color(0xFF9DD5BA),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFFB9F1D5),
    secondary = Color(0xFFB4CCBE),
)

@Composable
fun App() {
    val viewModel = remember { SimulationViewModel() }
    DisposableEffect(viewModel) {
        onDispose(viewModel::close)
    }
    val state by viewModel.state.collectAsState()

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) LotzDarkColors else LotzLightColors) {
        when (state.screen) {
            AppScreen.Configuration -> ConfigurationScreen(
                state = state,
                onConfigChange = viewModel::updateConfig,
                onGameSelected = viewModel::selectGame,
                onOptionToggled = viewModel::toggleOption,
                onRun = viewModel::startRun,
            )
            AppScreen.Running -> RunningScreen(state = state, onCancel = viewModel::cancelRun, onElapsedTick = viewModel::refreshElapsed)
            AppScreen.Results -> ResultsScreen(
                state = state,
                onConfigureAnother = viewModel::configureAnotherRun,
            )
        }
    }
}
