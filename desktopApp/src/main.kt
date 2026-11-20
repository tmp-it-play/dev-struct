package team.themoment.dev_struct

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.system.exitProcess

// 팝오버 크기(그림자 여백 포함)
private const val WIDTH = 360
private const val MAX_HEIGHT = 620

fun main(args: Array<String>) {
    if ("--dry-run" in args) dryRun()
    // 메뉴바 앱: Dock 아이콘을 숨기고, 메뉴바 아이콘은 다크/라이트에 맞춰 색이 바뀌게 한다
    System.setProperty("apple.awt.UIElement", "true")
    System.setProperty("apple.awt.enableTemplateImages", "true")
    LoginItem.syncOnStart()

    application {
        val scope = rememberCoroutineScope()
        var open by remember { mutableStateOf(LoginItem.BACKGROUND_ARG !in args) }
        var anchor by remember { mutableStateOf<Point?>(null) }
        var hiddenAt by remember { mutableStateOf(0L) }
        val trayIcon = remember { TrayIcon(trayImage(), "DevStruct").apply { isImageAutoSize = true } }
        val app =
            remember {
                DevStruct(scope) {
                    trayIcon.displayMessage("DevStruct", it, TrayIcon.MessageType.NONE)
                }.also { it.start() }
            }

        DisposableEffect(Unit) {
            trayIcon.addMouseListener(
                object : MouseAdapter() {
                    override fun mouseReleased(e: MouseEvent) {
                        // 열린 팝오버 밖(아이콘)을 누르면 포커스를 잃어 먼저 닫히므로 곧바로 다시 열지 않는다
                        if (System.currentTimeMillis() - hiddenAt < 300) return
                        anchor = e.locationOnScreen
                        open = !open
                    }
                },
            )
            SystemTray.getSystemTray().add(trayIcon)
            onDispose { SystemTray.getSystemTray().remove(trayIcon) }
        }

        val windowState = rememberWindowState(size = DpSize(WIDTH.dp, MAX_HEIGHT.dp))
        Window(
            onCloseRequest = { open = false },
            visible = open,
            title = "DevStruct",
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = true,
            state = windowState,
        ) {
            DisposableEffect(window) {
                val listener =
                    object : WindowAdapter() {
                        override fun windowLostFocus(e: WindowEvent) {
                            open = false
                            hiddenAt = System.currentTimeMillis()
                        }
                    }
                window.addWindowFocusListener(listener)
                onDispose { window.removeWindowFocusListener(listener) }
            }
            LaunchedEffect(open, anchor) {
                if (!open) return@LaunchedEffect
                window.location = popoverLocation(anchor)
                window.toFront()
                window.requestFocus()
            }
            Popover(app, onQuit = ::exitApplication) {
                windowState.size =
                    DpSize(WIDTH.dp, it.coerceAtMost(MAX_HEIGHT.dp))
            }
        }
    }
}

/** 메뉴바 아이콘 바로 아래, 화면 안쪽에 맞춘다. 아이콘 위치를 모르면 오른쪽 위. */
private fun popoverLocation(anchor: Point?): Point {
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration }
    val screen = screens.firstOrNull { anchor != null && it.bounds.contains(anchor) } ?: screens.first()
    val bounds = screen.bounds
    val top = bounds.y + Toolkit.getDefaultToolkit().getScreenInsets(screen).top
    val centerX = anchor?.x ?: (bounds.x + bounds.width - WIDTH / 2 - 8)
    return Point((centerX - WIDTH / 2).coerceIn(bounds.x, bounds.x + bounds.width - WIDTH), top)
}

/** 템플릿 이미지(알파만 사용)로 그린 폴더 모양 */
private fun trayImage(): BufferedImage {
    val image = BufferedImage(44, 44, BufferedImage.TYPE_INT_ARGB)
    image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color.BLACK
        stroke = BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        fill(RoundRectangle2D.Float(5f, 9f, 15f, 8f, 5f, 5f))
        draw(RoundRectangle2D.Float(5f, 13f, 34f, 23f, 7f, 7f))
        dispose()
    }
    return image
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
