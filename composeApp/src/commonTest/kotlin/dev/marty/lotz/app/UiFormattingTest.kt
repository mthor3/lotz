package dev.marty.lotz.app

import dev.marty.lotz.sim.rules.Games
import kotlin.test.Test
import kotlin.test.assertEquals

class UiFormattingTest {

    @Test
    fun jackpotOddsReflectTheNumberOfEntriesInOneDrawing() {
        assertEquals("1 in 292.2M", formatOddsForEntries(Games.powerball, 1))
        assertEquals("1 in 146.1M", formatOddsForEntries(Games.powerball, 2))
    }
}
