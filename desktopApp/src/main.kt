package team.themoment.dev_struct

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Devstruct") {
        App()
    }
}
