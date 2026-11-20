package team.themoment.dev_struct

import java.awt.Point
import java.awt.TrayIcon
import java.awt.geom.Point2D

/**
 * 메뉴바 아이콘의 왼쪽 끝 화면 좌표. AWT 공개 API가 없어 JDK 내부(CTrayIcon.nativeGetIconLocation)를 쓴다.
 * `--add-opens java.desktop/java.awt`, `java.desktop/sun.lwawt.macosx`가 없으면 null.
 */
fun trayIconLocation(icon: TrayIcon): Point? = runCatching {
    val peer = TrayIcon::class.java.getDeclaredField("peer").apply { isAccessible = true }.get(icon) ?: return null
    val ptr =
        Class
            .forName("sun.lwawt.macosx.CFRetainedResource")
            .getDeclaredField("ptr")
            .apply { isAccessible = true }
            .getLong(peer)
    val method = peer.javaClass.getDeclaredMethod("nativeGetIconLocation", Long::class.javaPrimitiveType).apply {
        isAccessible =
            true
    }
    val location = method.invoke(peer, ptr) as Point2D
    Point(location.x.toInt(), location.y.toInt())
}.getOrNull()
