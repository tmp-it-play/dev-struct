package team.themoment.dev_struct

import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences

object Settings {
    private val prefs = Preferences.userRoot().node("team/themoment/devstruct")
    private val home = System.getProperty("user.home")

    var root: String
        get() = prefs.get("root", "$home/Programming")
        set(value) = prefs.put("root", value)

    var intervalMinutes: Int
        get() = prefs.getInt("intervalMinutes", 60)
        set(value) = prefs.putInt("intervalMinutes", value.coerceAtLeast(1))

    /** true면 무조건 이동, false면 승인한 항목만 이동 */
    var autoMove: Boolean
        get() = prefs.getBoolean("autoMove", false)
        set(value) = prefs.putBoolean("autoMove", value)
}

/** 로그인 시 실행. executable-jar로 실행했을 때만 쓸 수 있다. */
object LoginItem {
    private val plist =
        Path.of(System.getProperty("user.home"), "Library/LaunchAgents/team.themoment.devstruct.plist")

    // `java -jar x.jar`로 실행되면 classpath가 그 jar 하나뿐이다 (`./kotlin run`이면 여러 개라 null)
    val jarPath: String? =
        System
            .getProperty("java.class.path")
            .takeIf { it.endsWith(".jar") && ':' !in it }
            ?.let { Path.of(it).toAbsolutePath().toString() }

    val enabled get() = Files.exists(plist)

    fun enable() {
        val jar = jarPath ?: return
        val java = ProcessHandle.current().info().command().orElse("/usr/bin/java")
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
                <array><string>$java</string><string>-jar</string><string>$jar</string></array>
                <key>RunAtLoad</key><true/>
            </dict>
            </plist>
            """.trimIndent(),
        )
    }

    fun disable() {
        Files.deleteIfExists(plist)
    }
}
