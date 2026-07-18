package dev.marty.lotz.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Lotz — Lottery strategy simulator",
        state = androidx.compose.ui.window.rememberWindowState(size = DpSize(1120.dp, 800.dp)),
    ) {
        window.minimumSize = java.awt.Dimension(390, 620)
        App()
    }
}
