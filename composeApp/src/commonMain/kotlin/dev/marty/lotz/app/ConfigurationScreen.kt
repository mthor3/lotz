package dev.marty.lotz.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.marty.lotz.sim.engine.BatchRunner
import dev.marty.lotz.sim.rules.GameDefinition
import dev.marty.lotz.sim.rules.GameOption
import dev.marty.lotz.sim.rules.Games

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(
    state: AppUiState,
    onConfigChange: ((SimulationConfig) -> SimulationConfig) -> Unit,
    onGameSelected: (String) -> Unit,
    onOptionToggled: (String) -> Unit,
    onRun: () -> Unit,
) {
    val config = state.config
    val validation = validateSimulationConfig(config)
    val game = Games.all.first { it.id == config.gameId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Lotz", fontWeight = FontWeight.Bold)
                        Text("Lottery strategy simulator", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                state.message?.let { MessageBanner(it) }

                Section(title = "Choose a game", subtitle = "Current rules, price, schedule, and jackpot odds") {
                    Games.all.forEach { candidate ->
                        GameCard(
                            game = candidate,
                            selected = candidate.id == config.gameId,
                            onClick = { onGameSelected(candidate.id) },
                        )
                    }
                }

                Section(title = "Build your strategy") {
                    val entries = config.entriesText.toIntOrNull()
                    OutlinedTextField(
                        value = config.entriesText,
                        onValueChange = { value -> onConfigChange { it.copy(entriesText = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Entries per drawing") },
                        trailingIcon = {
                            Column {
                                IconButton(
                                    onClick = { updateEntriesBy(config.entriesText, 1, onConfigChange) },
                                    enabled = config.entriesText.toIntOrNull()?.let { it < MAX_ENTRIES_PER_DRAWING } ?: false,
                                ) { Text("▲") }
                                IconButton(
                                    onClick = { updateEntriesBy(config.entriesText, -1, onConfigChange) },
                                    enabled = config.entriesText.toIntOrNull()?.let { it > 1 } ?: false,
                                ) { Text("▼") }
                            }
                        },
                        supportingText = {
                            Column {
                                entries?.takeIf { it in 1..MAX_ENTRIES_PER_DRAWING }?.let {
                                    Text("Jackpot odds for this drawing: ${formatOddsForEntries(game, it)}")
                                }
                                FieldHelp(validation.errors[ConfigFields.ENTRIES])
                            }
                        },
                        isError = ConfigFields.ENTRIES in validation.errors,
                        singleLine = true,
                    )

                    Text("Number picking", style = MaterialTheme.typography.titleSmall)
                    RadioChoice(
                        selected = config.pickMode == PickMode.NewEachDraw,
                        title = "New random numbers every drawing",
                        onClick = { onConfigChange { it.copy(pickMode = PickMode.NewEachDraw) } },
                    )
                    RadioChoice(
                        selected = config.pickMode == PickMode.SameEveryDraw,
                        title = "Same random numbers, picked once at the start",
                        onClick = { onConfigChange { it.copy(pickMode = PickMode.SameEveryDraw) } },
                    )
                    Text(
                        "Either way the odds are identical — every combination is equally likely each drawing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (game.options.isNotEmpty()) {
                        Text("Game options", style = MaterialTheme.typography.titleSmall)
                        game.options.forEach { option ->
                            val builtIn = option.priceCentsPerPlay == 0L
                            CheckChoice(
                                checked = option.id in config.selectedOptionIds,
                                enabled = !builtIn,
                                title = optionTitle(option),
                                subtitle = optionDescription(option, builtIn),
                                onClick = { onOptionToggled(option.id) },
                            )
                        }
                    }

                    Text("Play frequency", style = MaterialTheme.typography.titleSmall)
                    RadioChoice(
                        selected = config.frequencyMode == FrequencyMode.EveryDrawing,
                        title = "Every drawing",
                        onClick = { onConfigChange { it.copy(frequencyMode = FrequencyMode.EveryDrawing) } },
                    )
                    RadioChoice(
                        selected = config.frequencyMode == FrequencyMode.EveryNthDrawing,
                        title = "Every nth drawing",
                        onClick = { onConfigChange { it.copy(frequencyMode = FrequencyMode.EveryNthDrawing) } },
                    )
                    if (config.frequencyMode == FrequencyMode.EveryNthDrawing) {
                        OutlinedTextField(
                            value = config.frequencyEveryText,
                            onValueChange = { value -> onConfigChange { it.copy(frequencyEveryText = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Play every…") },
                            suffix = { Text("drawings") },
                            supportingText = { FieldHelp(validation.errors[ConfigFields.FREQUENCY]) },
                            isError = ConfigFields.FREQUENCY in validation.errors,
                            singleLine = true,
                        )
                    }
                }

                Section(title = "Choose when to stop") {
                    StopChoice(
                        selected = config.stopKind == StopKind.Budget,
                        title = "Budget",
                        subtitle = "Stop before the next purchase would exceed your stake",
                        onClick = { onConfigChange { it.copy(stopKind = StopKind.Budget) } },
                    )
                    if (config.stopKind == StopKind.Budget) {
                        OutlinedTextField(
                            value = config.budgetDollarsText,
                            onValueChange = { value -> onConfigChange { it.copy(budgetDollarsText = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Total budget") },
                            prefix = { Text("$") },
                            supportingText = { FieldHelp(validation.errors[ConfigFields.BUDGET]) },
                            isError = ConfigFields.BUDGET in validation.errors,
                            singleLine = true,
                        )
                        CheckChoice(
                            checked = config.reinvestWinnings && resolvedTrackWinnings(config),
                            enabled = resolvedTrackWinnings(config),
                            title = "Reinvest winnings",
                            subtitle = if (resolvedTrackWinnings(config)) {
                                "Prizes extend the available budget"
                            } else {
                                "Requires winnings tracking"
                            },
                            onClick = { onConfigChange { it.copy(reinvestWinnings = !it.reinvestWinnings) } },
                        )
                    }

                    StopChoice(
                        selected = config.stopKind == StopKind.Duration,
                        title = "Simulated duration",
                        subtitle = "Play through calendar time on the game's real schedule",
                        onClick = { onConfigChange { it.copy(stopKind = StopKind.Duration) } },
                    )
                    if (config.stopKind == StopKind.Duration) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = config.durationText,
                                onValueChange = { value -> onConfigChange { it.copy(durationText = value) } },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Duration") },
                                supportingText = { FieldHelp(validation.errors[ConfigFields.DURATION]) },
                                isError = ConfigFields.DURATION in validation.errors,
                                singleLine = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioChoice(
                                    modifier = Modifier.weight(1f),
                                    selected = config.durationUnit == DurationUnit.Months,
                                    title = "Months",
                                    onClick = { onConfigChange { it.copy(durationUnit = DurationUnit.Months) } },
                                )
                                RadioChoice(
                                    modifier = Modifier.weight(1f),
                                    selected = config.durationUnit == DurationUnit.Years,
                                    title = "Years",
                                    onClick = { onConfigChange { it.copy(durationUnit = DurationUnit.Years) } },
                                )
                            }
                        }
                    }

                    StopChoice(
                        selected = config.stopKind == StopKind.UntilJackpot,
                        title = "Until I hit the jackpot",
                        subtitle = "Unbounded cost and time; use cancel to stop the run",
                        onClick = { onConfigChange { it.copy(stopKind = StopKind.UntilJackpot) } },
                    )

                    Text("What to track", style = MaterialTheme.typography.titleSmall)
                    CheckChoice(
                        checked = resolvedTrackWinnings(config),
                        title = "Track winnings and jackpot market",
                        subtitle = "Off: runs are near-instant; only spend, match counts, and the jackpot hit are simulated",
                        onClick = {
                            onConfigChange { it.copy(trackWinnings = !resolvedTrackWinnings(it)) }
                        },
                    )
                    CheckChoice(
                        checked = config.trackSpend,
                        title = "Track money spent",
                        subtitle = "Show what the strategy costs over the run",
                        onClick = { onConfigChange { it.copy(trackSpend = !it.trackSpend) } },
                    )
                }

                Section(title = "Run mode") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeCard(
                            modifier = Modifier.fillMaxWidth(),
                            selected = config.runMode == RunMode.Single,
                            title = "Single run",
                            subtitle = "One detailed outcome",
                            onClick = { onConfigChange { it.copy(runMode = RunMode.Single) } },
                        )
                        ModeCard(
                            modifier = Modifier.fillMaxWidth(),
                            selected = config.runMode == RunMode.Batch,
                            title = "Monte Carlo",
                            subtitle = "Aggregate many runs",
                            onClick = { onConfigChange { it.copy(runMode = RunMode.Batch) } },
                        )
                    }
                    if (config.runMode == RunMode.Batch) {
                        OutlinedTextField(
                            value = config.batchRunsText,
                            onValueChange = { value -> onConfigChange { it.copy(batchRunsText = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Number of runs") },
                            supportingText = { FieldHelp(validation.errors[ConfigFields.BATCH_RUNS]) },
                            isError = ConfigFields.BATCH_RUNS in validation.errors,
                            singleLine = true,
                        )
                    }

                    if (
                        config.runMode == RunMode.Batch && config.stopKind == StopKind.UntilJackpot &&
                        !resolvedTrackWinnings(config)
                    ) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Text(
                                "Winnings tracking is off — this batch computes instantly.",
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (validation.expectedBatchDrawings != null && validation.expectedBatchDrawings > 0) {
                        FeasibilityCard(
                            estimate = validation.expectedBatchDrawings,
                            expensive = validation.requiresExpensiveOverride,
                        )
                        if (validation.requiresExpensiveOverride) {
                            CheckChoice(
                                checked = config.allowExpensiveUntilJackpot,
                                title = "Run this expensive batch anyway",
                                subtitle = "I understand this may take a long time",
                                onClick = {
                                    onConfigChange {
                                        it.copy(allowExpensiveUntilJackpot = !it.allowExpensiveUntilJackpot)
                                    }
                                },
                            )
                            validation.errors[ConfigFields.OVERRIDE]?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Section(title = "Reproducibility") {
                    OutlinedTextField(
                        value = config.seedText,
                        onValueChange = { value -> onConfigChange { it.copy(seedText = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Random seed (optional)") },
                        supportingText = {
                            FieldHelp(validation.errors[ConfigFields.SEED] ?: "Leave blank to generate a seed; results show the seed used.")
                        },
                        isError = ConfigFields.SEED in validation.errors,
                        singleLine = true,
                    )
                }

                val request = validation.request
                if (request != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            "${request.strategy.entriesPerDrawing} ${if (request.strategy.entriesPerDrawing == 1) "entry" else "entries"} · " +
                                "${formatMoney(request.strategy.costPerDrawingCents)} each played drawing",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Button(
                    onClick = onRun,
                    enabled = validation.isValid,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (config.runMode == RunMode.Single) "Run simulation" else "Run Monte Carlo batch")
                }
                if (!validation.isValid) {
                    Text(
                        "Fix the highlighted fields to run.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun updateEntriesBy(
    entriesText: String,
    delta: Int,
    onConfigChange: ((SimulationConfig) -> SimulationConfig) -> Unit,
) {
    val entries = entriesText.toIntOrNull() ?: return
    val updated = (entries + delta).coerceIn(1, MAX_ENTRIES_PER_DRAWING)
    onConfigChange { it.copy(entriesText = updated.toString()) }
}

@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        content()
        HorizontalDivider()
    }
}

@Composable
private fun GameCard(game: GameDefinition, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(game.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(formatMatrix(game), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${formatBasePrice(game)} · ${formatSchedule(game)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Jackpot odds ${formatOdds(game)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RadioChoice(
    selected: Boolean,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CheckChoice(
    checked: Boolean,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onClick() }, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StopChoice(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.padding(top = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModeCard(
    modifier: Modifier,
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FeasibilityCard(estimate: Long, expensive: Boolean) {
    Surface(
        color = if (expensive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Estimated work: about ${formatCompactCount(estimate)} drawings",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (expensive) {
                    "This exceeds the ${formatCompactCount(BatchRunner.UNTIL_JACKPOT_DRAWING_GUARDRAIL)}-drawing safety guardrail."
                } else {
                    "This is below the batch safety guardrail."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MessageBanner(message: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FieldHelp(text: String?) {
    if (text != null) Text(text)
}

private fun optionTitle(option: GameOption): String = when (option.id) {
    "power-play" -> "Power Play"
    "double-play" -> "Double Play"
    "built-in-multiplier" -> "Built-in multiplier"
    "kicker" -> "Kicker"
    else -> option.id.replace('-', ' ').replaceFirstChar(Char::uppercase)
}

private fun optionDescription(option: GameOption, builtIn: Boolean): String {
    val price = if (builtIn) "Included in every play" else "+${formatMoney(option.priceCentsPerPlay)} per play"
    return when (option.id) {
        "power-play" -> "$price · Multiplies non-jackpot prizes"
        "double-play" -> "$price · Same numbers in a second drawing"
        "built-in-multiplier" -> "$price · Random 2×–10× non-jackpot prizes"
        "kicker" -> "$price · Enhances Oregon Megabucks prizes"
        else -> price
    }
}
