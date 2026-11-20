package team.themoment.dev_struct

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if ("--dry-run" in args) dryRun()
    LoginItem.syncOnStart()
    application {
        val scope = rememberCoroutineScope()
        val trayState = rememberTrayState()
        var visible by remember { mutableStateOf(true) }
        val app =
            remember {
                DevStruct(scope) { trayState.sendNotification(Notification("DevStruct", it)) }.also { it.start() }
            }

        Tray(icon = TrayIcon, state = trayState, tooltip = "DevStruct", onAction = { visible = true }) {
            Item("열기") { visible = true }
            Item("지금 스캔", onClick = app::scanNow)
            Item("종료", onClick = ::exitApplication)
        }
        // 창을 닫아도 메뉴바에 남아 주기 스캔을 계속한다
        Window(
            onCloseRequest = { visible = false },
            visible = visible,
            title = "DevStruct",
            state = rememberWindowState(width = 900.dp, height = 700.dp),
        ) {
            App(app)
        }
    }
}

/** 아무것도 바꾸지 않고 계획만 출력한다. */
private fun dryRun(): Nothing {
    val root = Path.of(Settings.root)
    val plans = Organizer(root, ::println).scan()
    plans.filter { it.newRemote != null }.forEach { println("remote: ${root.relativize(it.dir)} → ${it.newRemote}") }
    plans.filter {
        it.needsMove
    }.forEach { println("move:   ${root.relativize(it.dir)} → ${root.relativize(it.target)}") }
    println("레포 ${plans.size}개, IDE 실행 중: ${JetBrains.runningIdes().ifEmpty { listOf("없음") }.joinToString()}")
    exitProcess(0)
}

private object TrayIcon : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val stroke = Stroke(width = size.minDimension / 10)
        drawRect(
            Color.Black,
            Offset(size.width * 0.1f, size.height * 0.25f),
            Size(size.width * 0.8f, size.height * 0.6f),
            style = stroke,
        )
        drawLine(
            Color.Black,
            Offset(size.width * 0.1f, size.height * 0.25f),
            Offset(
                size.width * 0.45f,
                size.height * 0.25f,
            ),
            stroke.width * 2,
        )
    }
}
