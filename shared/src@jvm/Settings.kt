package team.themoment.dev_struct

import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences

object Settings {
    private val prefs = Preferences.userRoot().node("team/themoment/devstruct")
    private val home = System.getProperty("user.home")

    var root: String
        // DEVSTRUCT_ROOT로 덮어쓸 수 있다 (테스트용)
        get() = System.getenv("DEVSTRUCT_ROOT") ?: prefs.get("root", "$home/Programming")
        set(value) = prefs.put("root", value)

    var intervalMinutes: Int
        get() = prefs.getInt("intervalMinutes", 60)
        set(value) = prefs.putInt("intervalMinutes", value.coerceAtLeast(1))

    /** true면 무조건 이동, false면 승인한 항목만 이동 */
    var autoMove: Boolean
        get() = prefs.getBoolean("autoMove", false)
        set(value) = prefs.putBoolean("autoMove", value)

    /** 개발 도구가 켜져 있으면 이동을 미루고, 옮긴 뒤 도구들의 프로젝트 경로를 바꾼다(키는 예전 이름 유지) */
    var devTools: Boolean
        get() = prefs.getBoolean("jetbrains", true)
        set(value) = prefs.putBoolean("jetbrains", value)
}

/** 로그인 시 실행. .app 번들로 실행했을 때만 쓸 수 있다. */
object LoginItem {
    /** 로그인 시 실행되면 팝오버를 띄우지 않는다 */
    const val BACKGROUND_ARG = "--background"

    private val plist =
        Path.of(System.getProperty("user.home"), "Library/LaunchAgents/team.themoment.devstruct.plist")

    val available get() = Updater.appBundle != null

    val enabled get() = Files.exists(plist)

    fun enable() {
        val bundle = Updater.appBundle ?: return
        Files.createDirectories(plist.parent)
        Files.writeString(
            plist,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key><string>team.themoment.devstruct</string>
                <key>ProgramArguments</key>
                <array><string>/usr/bin/open</string><string>$bundle</string><string>--args</string><string>$BACKGROUND_ARG</string></array>
                <key>RunAtLoad</key><true/>
            </dict>
            </plist>
            """.trimIndent(),
        )
    }

    /** 기본은 꺼짐. 켜져 있으면 앱 위치가 바뀌었을 수 있으니 다시 쓴다. */
    fun syncOnStart() {
        if (available && enabled) enable()
    }

    fun disable() {
        Files.deleteIfExists(plist)
    }
}
