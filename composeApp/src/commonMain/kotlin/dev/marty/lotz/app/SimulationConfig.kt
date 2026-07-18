package dev.marty.lotz.app

import dev.marty.lotz.sim.engine.BatchRunner
import dev.marty.lotz.sim.engine.NumberChoice
import dev.marty.lotz.sim.engine.PlayFrequency
import dev.marty.lotz.sim.engine.PlayerStrategy
import dev.marty.lotz.sim.engine.StopCondition
import dev.marty.lotz.sim.engine.TrackingOptions
import dev.marty.lotz.sim.rules.GameDefinition
import dev.marty.lotz.sim.rules.Games
import kotlinx.datetime.DatePeriod

enum class RunMode { Single, Batch }

enum class FrequencyMode { EveryDrawing, EveryNthDrawing }

enum class StopKind { Budget, Duration, UntilJackpot }

enum class DurationUnit { Months, Years }

enum class PickMode { NewEachDraw, SameEveryDraw }

data class SimulationConfig(
    val gameId: String = Games.megabucks.id,
    val entriesText: String = "1",
    val selectedOptionIds: Set<String> = emptySet(),
    val frequencyMode: FrequencyMode = FrequencyMode.EveryDrawing,
    val frequencyEveryText: String = "2",
    val runMode: RunMode = RunMode.Single,
    val batchRunsText: String = "1000",
    val stopKind: StopKind = StopKind.Duration,
    val budgetDollarsText: String = "1000",
    val durationText: String = "1",
    val durationUnit: DurationUnit = DurationUnit.Years,
    val reinvestWinnings: Boolean = false,
    val seedText: String = "",
    val allowExpensiveUntilJackpot: Boolean = false,
    val pickMode: PickMode = PickMode.NewEachDraw,
    /** Null = automatic: follows [defaultTrackWinnings] until the user touches the toggle. */
    val trackWinnings: Boolean? = null,
    val trackSpend: Boolean = true,
)

/**
 * Winnings tracking defaults off where per-drawing simulation is prohibitively long and the money
 * detail uninteresting: until-jackpot runs and durations beyond 50 years.
 */
fun defaultTrackWinnings(config: SimulationConfig): Boolean = when (config.stopKind) {
    StopKind.UntilJackpot -> false
    StopKind.Duration -> {
        val value = config.durationText.toIntOrNull()
        val months = when (config.durationUnit) {
            DurationUnit.Months -> value
            DurationUnit.Years -> value?.let { it * 12 }
        }
        months == null || months <= 50 * 12
    }
    StopKind.Budget -> true
}

fun resolvedTrackWinnings(config: SimulationConfig): Boolean =
    config.trackWinnings ?: defaultTrackWinnings(config)

data class SimulationRequest(
    val strategy: PlayerStrategy,
    val runMode: RunMode,
    val batchRuns: Int,
    val requestedSeed: Long?,
    val allowExpensiveUntilJackpot: Boolean,
)

data class ConfigValidation(
    val errors: Map<String, String>,
    val request: SimulationRequest?,
    val expectedBatchDrawings: Long?,
) {
    val isValid: Boolean get() = request != null && errors.isEmpty()
    val requiresExpensiveOverride: Boolean
        get() = expectedBatchDrawings != null &&
            expectedBatchDrawings > BatchRunner.UNTIL_JACKPOT_DRAWING_GUARDRAIL
}

object ConfigFields {
    const val ENTRIES = "entries"
    const val FREQUENCY = "frequency"
    const val BATCH_RUNS = "batchRuns"
    const val BUDGET = "budget"
    const val DURATION = "duration"
    const val SEED = "seed"
    const val OVERRIDE = "override"
}

private const val MAX_ENTRIES_PER_DRAWING = 1_000
private const val MAX_FREQUENCY_INTERVAL = 1_000
private const val MAX_BATCH_RUNS = 10_000
private const val MAX_DURATION_MONTHS = 1_200
private const val MAX_DURATION_YEARS = 100
private const val MAX_BUDGET_CENTS = 100_000_000_000L // $1 billion

fun validateSimulationConfig(config: SimulationConfig): ConfigValidation {
    val errors = linkedMapOf<String, String>()
    val game = Games.all.firstOrNull { it.id == config.gameId } ?: Games.megabucks

    val entries = config.entriesText.toIntOrNull()
    if (entries == null || entries !in 1..MAX_ENTRIES_PER_DRAWING) {
        errors[ConfigFields.ENTRIES] = "Enter 1–$MAX_ENTRIES_PER_DRAWING entries."
    }

    val frequency = when (config.frequencyMode) {
        FrequencyMode.EveryDrawing -> PlayFrequency.EveryDrawing
        FrequencyMode.EveryNthDrawing -> {
            val every = config.frequencyEveryText.toIntOrNull()
            if (every == null || every !in 2..MAX_FREQUENCY_INTERVAL) {
                errors[ConfigFields.FREQUENCY] = "Enter an interval from 2 to $MAX_FREQUENCY_INTERVAL."
                null
            } else {
                PlayFrequency.EveryNthDrawing(every)
            }
        }
    }

    val stopCondition = when (config.stopKind) {
        StopKind.Budget -> {
            val cents = parseDollarsToCents(config.budgetDollarsText)
            if (cents == null || cents !in 1..MAX_BUDGET_CENTS) {
                errors[ConfigFields.BUDGET] = "Enter a budget from $0.01 to $1,000,000,000."
                null
            } else {
                StopCondition.BudgetCap(cents)
            }
        }
        StopKind.Duration -> {
            val value = config.durationText.toIntOrNull()
            val maximum = when (config.durationUnit) {
                DurationUnit.Months -> MAX_DURATION_MONTHS
                DurationUnit.Years -> MAX_DURATION_YEARS
            }
            if (value == null || value !in 1..maximum) {
                errors[ConfigFields.DURATION] = "Enter 1–$maximum ${config.durationUnit.name.lowercase()}."
                null
            } else {
                StopCondition.Duration(
                    when (config.durationUnit) {
                        DurationUnit.Months -> DatePeriod(months = value)
                        DurationUnit.Years -> DatePeriod(years = value)
                    },
                )
            }
        }
        StopKind.UntilJackpot -> StopCondition.UntilJackpot
    }

    val batchRuns = if (config.runMode == RunMode.Batch) {
        config.batchRunsText.toIntOrNull().also { runs ->
            if (runs == null || runs !in 2..MAX_BATCH_RUNS) {
                errors[ConfigFields.BATCH_RUNS] = "Enter 2–$MAX_BATCH_RUNS runs."
            }
        }
    } else {
        1
    }

    val requestedSeed = if (config.seedText.isBlank()) {
        null
    } else {
        config.seedText.trim().toLongOrNull().also {
            if (it == null) errors[ConfigFields.SEED] = "Enter a whole number, or leave blank for a random seed."
        }
    }

    val validOptionIds = game.options.mapTo(mutableSetOf()) { it.id }
    val optionIds = config.selectedOptionIds.intersect(validOptionIds)

    val trackWinnings = resolvedTrackWinnings(config)
    val strategy = if (entries != null && frequency != null && stopCondition != null) {
        PlayerStrategy(
            game = game,
            entriesPerDrawing = entries,
            optionIds = optionIds,
            numberChoice = when (config.pickMode) {
                PickMode.NewEachDraw -> NumberChoice.QuickPick
                PickMode.SameEveryDraw -> NumberChoice.RandomOnce
            },
            frequency = frequency,
            stopCondition = stopCondition,
            reinvestWinnings = config.reinvestWinnings && config.stopKind == StopKind.Budget && trackWinnings,
            tracking = TrackingOptions(trackWinnings = trackWinnings, trackSpend = config.trackSpend),
        )
    } else {
        null
    }

    val expectedBatchDrawings = if (
        strategy != null && batchRuns != null &&
        config.runMode == RunMode.Batch && config.stopKind == StopKind.UntilJackpot
    ) {
        BatchRunner.estimateExpectedDrawings(strategy, batchRuns)
    } else {
        null
    }

    if (
        expectedBatchDrawings != null &&
        expectedBatchDrawings > BatchRunner.UNTIL_JACKPOT_DRAWING_GUARDRAIL &&
        !config.allowExpensiveUntilJackpot
    ) {
        errors[ConfigFields.OVERRIDE] = "Review the estimate and explicitly allow this expensive batch."
    }

    val request = if (strategy != null && batchRuns != null && requestedSeedParsed(config, requestedSeed)) {
        SimulationRequest(
            strategy = strategy,
            runMode = config.runMode,
            batchRuns = batchRuns,
            requestedSeed = requestedSeed,
            allowExpensiveUntilJackpot = config.allowExpensiveUntilJackpot,
        )
    } else {
        null
    }

    return ConfigValidation(errors, request?.takeIf { errors.isEmpty() }, expectedBatchDrawings)
}

private fun requestedSeedParsed(config: SimulationConfig, parsed: Long?): Boolean =
    config.seedText.isBlank() || parsed != null

/** Strict, locale-neutral form parsing. Commas and a leading dollar sign are accepted. */
fun parseDollarsToCents(input: String): Long? {
    val normalized = input.trim().removePrefix("$").replace(",", "")
    if (!Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(normalized)) return null
    val parts = normalized.split('.')
    val dollars = parts[0].toLongOrNull() ?: return null
    val cents = when (parts.getOrNull(1)?.length) {
        null -> 0L
        1 -> parts[1].toLong() * 10
        else -> parts[1].toLong()
    }
    if (dollars > (Long.MAX_VALUE - cents) / 100) return null
    return dollars * 100 + cents
}

fun defaultOptionsFor(game: GameDefinition): Set<String> =
    game.options.filter { it.priceCentsPerPlay == 0L }.mapTo(mutableSetOf()) { it.id }
