package dev.marty.lotz.app

import dev.marty.lotz.sim.engine.BatchConfig
import dev.marty.lotz.sim.engine.BatchProgress
import dev.marty.lotz.sim.engine.BatchResult
import dev.marty.lotz.sim.engine.BatchRunner
import dev.marty.lotz.sim.engine.DrawingProgress
import dev.marty.lotz.sim.engine.RunResult
import dev.marty.lotz.sim.engine.SimulationEngine
import dev.marty.lotz.sim.rules.Games
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.random.Random

enum class AppScreen { Configuration, Running, Results }

sealed interface SimulationProgress {
    data class Single(val progress: DrawingProgress) : SimulationProgress
    data class Batch(val progress: BatchProgress) : SimulationProgress
}

sealed interface SimulationResult {
    data class Single(val result: RunResult) : SimulationResult
    data class Batch(val result: BatchResult) : SimulationResult
}

data class AppUiState(
    val screen: AppScreen = AppScreen.Configuration,
    val config: SimulationConfig = SimulationConfig(),
    val progress: SimulationProgress? = null,
    val result: SimulationResult? = null,
    val activeSeed: Long? = null,
    val elapsedMillis: Long = 0L,
    val runStartedAtMillis: Long = 0L,
    val message: String? = null,
)

class SimulationViewModel(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private var activeJob: Job? = null
    private var runGeneration = 0L

    fun updateConfig(transform: (SimulationConfig) -> SimulationConfig) {
        if (mutableState.value.screen != AppScreen.Configuration) return
        mutableState.update { it.copy(config = transform(it.config), message = null) }
    }

    fun selectGame(gameId: String) {
        val game = Games.all.firstOrNull { it.id == gameId } ?: return
        updateConfig {
            it.copy(
                gameId = game.id,
                selectedOptionIds = defaultOptionsFor(game),
                allowExpensiveUntilJackpot = false,
            )
        }
    }

    fun toggleOption(optionId: String) {
        val game = Games.all.first { it.id == mutableState.value.config.gameId }
        val option = game.options.firstOrNull { it.id == optionId } ?: return
        if (option.priceCentsPerPlay == 0L) return
        updateConfig { config ->
            val selected = config.selectedOptionIds.toMutableSet()
            if (!selected.add(optionId)) selected.remove(optionId)
            config.copy(selectedOptionIds = selected)
        }
    }

    fun startRun() {
        if (activeJob?.isActive == true) return
        val validation = validateSimulationConfig(mutableState.value.config)
        val request = validation.request
        if (request == null) {
            mutableState.update { it.copy(message = "Fix the highlighted fields before running.") }
            return
        }

        val seed = request.requestedSeed ?: Random.Default.nextLong()
        val generation = ++runGeneration
        val startedAt = Clock.System.now().toEpochMilliseconds()
        mutableState.update {
            it.copy(
                screen = AppScreen.Running,
                progress = null,
                result = null,
                activeSeed = seed,
                elapsedMillis = 0L,
                runStartedAtMillis = startedAt,
                message = null,
            )
        }

        activeJob = scope.launch {
            try {
                val result = when (request.runMode) {
                    RunMode.Single -> {
                        val runResult = SimulationEngine.run(request.strategy, seed) { progress ->
                            if (progress.drawingsSimulated == 1 || progress.drawingsSimulated % 250 == 0) {
                                mutableState.update { state ->
                                    state.copy(progress = SimulationProgress.Single(progress))
                                }
                            }
                        }
                        SimulationResult.Single(runResult)
                    }
                    RunMode.Batch -> {
                        val batchResult = BatchRunner.run(
                            BatchConfig(
                                strategy = request.strategy,
                                runs = request.batchRuns,
                                masterSeed = seed,
                                allowExpensiveUntilJackpot = request.allowExpensiveUntilJackpot,
                            ),
                        ) { progress ->
                            mutableState.update { state ->
                                state.copy(progress = SimulationProgress.Batch(progress))
                            }
                        }
                        SimulationResult.Batch(batchResult)
                    }
                }
                mutableState.update {
                    it.copy(
                        screen = AppScreen.Results,
                        progress = null,
                        result = result,
                        elapsedMillis = Clock.System.now().toEpochMilliseconds() - startedAt,
                    )
                }
            } catch (_: CancellationException) {
                // cancelRun already returns the user to configuration.
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        screen = AppScreen.Configuration,
                        progress = null,
                        result = null,
                        message = error.message ?: "The simulation could not be completed.",
                    )
                }
            } finally {
                if (generation == runGeneration) activeJob = null
            }
        }
    }

    fun refreshElapsed() {
        val state = mutableState.value
        if (state.screen == AppScreen.Running && activeJob?.isActive == true) {
            mutableState.update { it.copy(elapsedMillis = Clock.System.now().toEpochMilliseconds() - it.runStartedAtMillis) }
        }
    }

    fun cancelRun() {
        runGeneration++
        activeJob?.cancel()
        activeJob = null
        mutableState.update {
            it.copy(
                screen = AppScreen.Configuration,
                progress = null,
                result = null,
                message = "Run canceled.",
            )
        }
    }

    fun configureAnotherRun() {
        if (activeJob?.isActive == true) return
        mutableState.update {
            it.copy(
                screen = AppScreen.Configuration,
                progress = null,
                result = null,
                message = null,
            )
        }
    }

    fun close() {
        runGeneration++
        activeJob?.cancel()
        scope.cancel()
    }
}
