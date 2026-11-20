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
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.system.exitProcess

// 팝오버 크기
private const val WIDTH = 340
private const val HEIGHT = 640

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
        // 열면서 앱을 활성화하는 동안 포커스가 잠깐 오가므로, 직후의 닫기 신호는 무시한다
        var openedAt by remember { mutableStateOf(0L) }
        val justOpened = { System.currentTimeMillis() - openedAt < 500 }
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
            // 알림을 누르면 팝오버를 연다
            trayIcon.addActionListener {
                anchor = null
                open = true
            }
            SystemTray.getSystemTray().add(trayIcon)
            onDispose { SystemTray.getSystemTray().remove(trayIcon) }
        }

        val windowState = rememberWindowState(size = DpSize(WIDTH.dp, HEIGHT.dp))
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
                            if (justOpened()) return
                            open = false
                            hiddenAt = System.currentTimeMillis()
                        }
                    }
                window.addWindowFocusListener(listener)
                onDispose { window.removeWindowFocusListener(listener) }
            }
            DisposableEffect(window) {
                // 끌어서 옮길 수 없게 하고, 그림자는 macOS가 창 모양대로 그리게 한다
                window.rootPane.putClientProperty("apple.awt.draggableWindowBackground", false)
                window.rootPane.putClientProperty("Window.shadow", true)
                onDispose {}
            }
            LaunchedEffect(open, anchor) {
                if (!open) return@LaunchedEffect
                openedAt = System.currentTimeMillis()
                // 메뉴바 앱은 저절로 활성 앱이 되지 않아 포커스를 잃는 일도 없다. 활성화해야 다른 앱을 누를 때 닫힌다
                runCatching { Desktop.getDesktop().requestForeground(true) }
                // 창이 실제로 뜬 뒤에 포커스를 줘야 첫 클릭이 창 활성화에 먹히지 않고 버튼에 전달된다
                repeat(3) {
                    window.toFront()
                    window.requestFocus()
                    delay(60)
                }
                var placed: Point? = null
                while (true) {
                    // 다른 메뉴바 앱처럼 팝오버 바깥을 누르면 닫는다(포커스 이벤트가 오지 않는 경우까지)
                    if (!justOpened() && GlobalMouse.pressed() &&
                        !window.bounds.contains(MouseInfo.getPointerInfo().location)
                    ) {
                        open = false
                        hiddenAt = System.currentTimeMillis()
                        break
                    }
                    // 열려 있는 동안 아이콘 위치를 따라간다
                    // 아이콘 위치가 실제로 바뀔 때만 옮긴다(클릭 도중 창을 다시 놓으면 클릭이 취소된다)
                    val location = popoverLocation(trayIconLocation(trayIcon), anchor)
                    if (location != placed) {
                        windowState.position = WindowPosition(location.x.dp, location.y.dp)
                        placed = location
                    }
                    delay(100)
                }
            }
            Popover(app, onQuit = ::exitApplication, onClose = {
                open = false
                hiddenAt = System.currentTimeMillis()
            })
        }
    }
}

/**
 * 팝오버 본문의 왼쪽 위 모서리를 메뉴바 아이콘의 왼쪽 끝 바로 아래에 맞춘다(화면 밖으로 나가면 안쪽으로 민다).
 * 아이콘 위치를 못 구하면 클릭 지점을, 그것도 없으면 화면 오른쪽 위를 쓴다.
 */
private fun popoverLocation(icon: Point?, click: Point?): Point {
    val reference = icon ?: click
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration }
    val screen =
        screens.firstOrNull { reference != null && it.bounds.contains(reference.x, it.bounds.y) } ?: screens.first()
    val bounds = screen.bounds
    val menuBarBottom = bounds.y + Toolkit.getDefaultToolkit().getScreenInsets(screen).top
    val left = icon?.x ?: click?.let { it.x - 12 } ?: (bounds.x + bounds.width - WIDTH - 8)
    return Point(left.coerceIn(bounds.x + 6, bounds.x + bounds.width - WIDTH - 6), menuBarBottom)
}

/** 템플릿 이미지(알파만 사용). 로고처럼 꽉 찬 폴더에 트리 구조를 뚫어 다른 메뉴바 아이콘과 구분되게 한다 */
private fun trayImage(): BufferedImage {
    val image = BufferedImage(44, 44, BufferedImage.TYPE_INT_ARGB)
    image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color.BLACK
        val folder = Area(RoundRectangle2D.Float(4f, 8f, 16f, 10f, 5f, 5f))
        folder.add(Area(RoundRectangle2D.Float(4f, 12f, 36f, 25f, 8f, 8f)))
        fill(folder)
        composite = AlphaComposite.Clear
        stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        draw(Line2D.Float(13f, 19f, 13f, 31f))
        draw(Line2D.Float(13f, 25f, 20f, 25f))
        draw(Line2D.Float(13f, 31f, 20f, 31f))
        fill(Ellipse2D.Float(10f, 16f, 6f, 6f))
        fill(RoundRectangle2D.Float(23f, 22.5f, 11f, 5f, 5f, 5f))
        fill(RoundRectangle2D.Float(23f, 28.5f, 8f, 5f, 5f, 5f))
        dispose()
    }
    return image
}

/** 아무것도 바꾸지 않고 계획만 출력한다. */
private fun dryRun(): Nothing {
    val root = Path.of(Settings.root)
    val plans = Organizer(root, ::println).scan().plans
    plans.filter { it.newRemote != null }.forEach { println("remote: ${root.relativize(it.dir)} → ${it.newRemote}") }
    plans.filter {
        it.needsMove
    }.forEach { println("move:   ${root.relativize(it.dir)} → ${root.relativize(it.target)}") }
    val home = Path.of(System.getProperty("user.home"))
    kotlinx.coroutines.runBlocking { Discovery(home, root).scan(pause = 0) }.forEach {
        println(
            "found:  ${it.dir.toString().replaceFirst(
                home.toString(),
                "~",
            )} (${it.ref.fullName})${if (it.pinned) " [고정]" else ""}",
        )
    }
    println("저장소 ${plans.size}개, IDE 실행 중: ${DevTools.running().ifEmpty { listOf("없음") }.joinToString()}")
    println("에이전트 실행 중: ${DevTools.runningAgents().ifEmpty { listOf("없음") }.joinToString()}")
    exitProcess(0)
}
