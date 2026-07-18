package dev.marty.lotz.app

import dev.marty.lotz.sim.engine.BatchRunner
import dev.marty.lotz.sim.engine.NumberChoice
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
        // Winnings tracking must be forced on: untracked until-jackpot batches are analytic and exempt.
        val config = SimulationConfig(
            gameId = Games.powerball.id,
            runMode = RunMode.Batch,
            batchRunsText = "2",
            stopKind = StopKind.UntilJackpot,
            trackWinnings = true,
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
    fun trackWinningsDefaultsFollowTheStopCondition() {
        assertTrue(defaultTrackWinnings(SimulationConfig(stopKind = StopKind.Budget)))
        assertTrue(defaultTrackWinnings(SimulationConfig(stopKind = StopKind.Duration, durationText = "50", durationUnit = DurationUnit.Years)))
        assertFalse(defaultTrackWinnings(SimulationConfig(stopKind = StopKind.Duration, durationText = "60", durationUnit = DurationUnit.Years)))
        assertFalse(defaultTrackWinnings(SimulationConfig(stopKind = StopKind.Duration, durationText = "700", durationUnit = DurationUnit.Months)))
        assertTrue(defaultTrackWinnings(SimulationConfig(stopKind = StopKind.Duration, durationText = "600", durationUnit = DurationUnit.Months)))
        assertFalse(defaultTrackWinnings(SimulationConfig(stopKind = StopKind.UntilJackpot)))
    }

    @Test
    fun explicitTrackingChoiceIsPinnedAcrossStopKindChanges() {
        val pinnedOn = SimulationConfig(stopKind = StopKind.UntilJackpot, trackWinnings = true)
        assertTrue(resolvedTrackWinnings(pinnedOn))
        assertTrue(resolvedTrackWinnings(pinnedOn.copy(stopKind = StopKind.Budget)))

        val automatic = SimulationConfig(stopKind = StopKind.Budget)
        assertTrue(resolvedTrackWinnings(automatic))
        assertFalse(resolvedTrackWinnings(automatic.copy(stopKind = StopKind.UntilJackpot)))
    }

    @Test
    fun untrackedStrategySkipsWinningsAndForcesReinvestOff() {
        val validation = validateSimulationConfig(
            SimulationConfig(
                stopKind = StopKind.UntilJackpot,
                reinvestWinnings = true,
            ),
        )
        val strategy = assertNotNull(validation.request).strategy
        assertFalse(strategy.tracking.trackWinnings)
        assertFalse(strategy.reinvestWinnings)
    }

    @Test
    fun pickModeMapsToTheEngineNumberChoice() {
        val quick = validateSimulationConfig(SimulationConfig(pickMode = PickMode.NewEachDraw))
        assertEquals(NumberChoice.QuickPick, quick.request!!.strategy.numberChoice)

        val fixed = validateSimulationConfig(SimulationConfig(pickMode = PickMode.SameEveryDraw))
        assertEquals(NumberChoice.RandomOnce, fixed.request!!.strategy.numberChoice)
    }

    @Test
    fun untrackedUntilJackpotBatchNeedsNoExpensiveOverride() {
        val validation = validateSimulationConfig(
            SimulationConfig(
                gameId = Games.powerball.id,
                runMode = RunMode.Batch,
                batchRunsText = "1000",
                stopKind = StopKind.UntilJackpot,
            ),
        )
        assertEquals(0L, validation.expectedBatchDrawings)
        assertFalse(validation.requiresExpensiveOverride)
        assertTrue(validation.isValid)
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
    fun viewModelCompletesAnUntilJackpotRunInstantlyViaTheAnalyticPath() = runTest {
        val viewModel = SimulationViewModel(this)
        viewModel.selectGame(Games.powerball.id)
        viewModel.updateConfig { it.copy(stopKind = StopKind.UntilJackpot, seedText = "5") }

        viewModel.startRun()
        testScheduler.advanceUntilIdle()

        val finalState = viewModel.state.value
        assertEquals(AppScreen.Results, finalState.screen)
        val result = assertIs<SimulationResult.Single>(finalState.result).result
        assertTrue(result.jackpotWon)
        assertFalse(result.winningsTracked)
        assertNotNull(result.simulatedYears)
    }

    @Test
    fun startRunFromTheResultsScreenRerunsTheCurrentSettings() = runTest {
        val viewModel = SimulationViewModel(this)
        viewModel.selectGame(Games.powerball.id)
        viewModel.updateConfig { it.copy(stopKind = StopKind.UntilJackpot, seedText = "5") }
        viewModel.startRun()
        testScheduler.advanceUntilIdle()
        val firstResult = assertIs<SimulationResult.Single>(viewModel.state.value.result).result

        // "Run again" on the results screen calls startRun directly, without returning to configuration.
        viewModel.startRun()
        testScheduler.advanceUntilIdle()

        val rerunState = viewModel.state.value
        assertEquals(AppScreen.Results, rerunState.screen)
        val rerunResult = assertIs<SimulationResult.Single>(rerunState.result).result
        assertEquals(firstResult, rerunResult, "a pinned seed must reproduce the same outcome")
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
