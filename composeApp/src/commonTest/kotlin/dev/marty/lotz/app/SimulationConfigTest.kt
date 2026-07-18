package dev.marty.lotz.app

import dev.marty.lotz.sim.engine.BatchRunner
import dev.marty.lotz.sim.rules.Games
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimulationConfigTest {

    @Test
    fun parsesMoneyWithoutFloatingPointRounding() {
        assertEquals(123_45L, parseDollarsToCents("$123.45"))
        assertEquals(123_40L, parseDollarsToCents(" 123.4 "))
        assertEquals(123_456_00L, parseDollarsToCents("123,456"))
        assertNull(parseDollarsToCents("1.234"))
        assertNull(parseDollarsToCents("-4"))
        assertNull(parseDollarsToCents("free"))
    }

    @Test
    fun defaultOneYearSingleRunIsValid() {
        val validation = validateSimulationConfig(SimulationConfig())

        assertTrue(validation.isValid)
        assertEquals(RunMode.Single, validation.request?.runMode)
        assertEquals(1, validation.request?.batchRuns)
    }

    @Test
    fun rejectsInvalidNumericFieldsAndSeed() {
        val validation = validateSimulationConfig(
            SimulationConfig(
                entriesText = "0",
                frequencyMode = FrequencyMode.EveryNthDrawing,
                frequencyEveryText = "1",
                stopKind = StopKind.Budget,
                budgetDollarsText = "12.345",
                runMode = RunMode.Batch,
                batchRunsText = "10001",
                seedText = "not-a-seed",
            ),
        )

        assertFalse(validation.isValid)
        assertEquals(
            setOf(
                ConfigFields.ENTRIES,
                ConfigFields.FREQUENCY,
                ConfigFields.BUDGET,
                ConfigFields.BATCH_RUNS,
                ConfigFields.SEED,
            ),
            validation.errors.keys,
        )
    }

    @Test
    fun strategyIncludesSelectedOptionsAndAccurateDrawingCost() {
        val validation = validateSimulationConfig(
            SimulationConfig(
                gameId = Games.powerball.id,
                entriesText = "2",
                selectedOptionIds = setOf("power-play", "double-play"),
            ),
        )

        val strategy = assertNotNull(validation.request).strategy
        assertEquals(setOf("power-play", "double-play"), strategy.optionIds)
        assertEquals(800L, strategy.costPerDrawingCents)
    }

    @Test
    fun expensiveUntilJackpotBatchRequiresExplicitOverride() {
        val config = SimulationConfig(
            gameId = Games.powerball.id,
            runMode = RunMode.Batch,
            batchRunsText = "2",
            stopKind = StopKind.UntilJackpot,
        )

        val blocked = validateSimulationConfig(config)
        assertTrue(blocked.expectedBatchDrawings!! > BatchRunner.UNTIL_JACKPOT_DRAWING_GUARDRAIL)
        assertTrue(blocked.requiresExpensiveOverride)
        assertFalse(blocked.isValid)
        assertTrue(ConfigFields.OVERRIDE in blocked.errors)

        val allowed = validateSimulationConfig(config.copy(allowExpensiveUntilJackpot = true))
        assertTrue(allowed.isValid)
        assertTrue(allowed.request!!.allowExpensiveUntilJackpot)
    }

    @Test
    fun viewModelLaunchesOneYearPowerballRunToResults() = runTest {
        val viewModel = SimulationViewModel(this)
        viewModel.selectGame(Games.powerball.id)
        viewModel.updateConfig { it.copy(seedText = "42") }

        viewModel.startRun()
        testScheduler.advanceUntilIdle()

        val finalState = viewModel.state.value
        assertEquals(AppScreen.Results, finalState.screen)
        assertEquals(42L, finalState.activeSeed)
        val result = assertIs<SimulationResult.Single>(finalState.result).result
        assertEquals(Games.powerball.id, result.strategy.game.id)
        assertTrue(result.drawingsPlayed > 100)
        assertEquals(42L, result.seed)
    }

    @Test
    fun viewModelLaunchesBatchRunToResults() = runTest {
        val viewModel = SimulationViewModel()
        viewModel.selectGame(Games.megabucks.id)
        viewModel.updateConfig {
            it.copy(runMode = RunMode.Batch, batchRunsText = "4", seedText = "7")
        }

        viewModel.startRun()
        val finalState = withContext(Dispatchers.Default) {
            withTimeout(10_000) {
                viewModel.state.first { it.screen == AppScreen.Results }
            }
        }

        val result = assertIs<SimulationResult.Batch>(finalState.result).result
        assertEquals(4, result.stats.runs)
        assertEquals(7L, result.config.masterSeed)
        viewModel.close()
    }
}
