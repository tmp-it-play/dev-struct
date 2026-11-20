package team.themoment.dev_struct

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

// CD가 태그(vX.Y.Z)와 이 값이 같은지 검사한다
const val APP_VERSION = "2.0.1"

data class Release(val tag: String, val asset: String, val url: String)

/** 최신 정식 릴리스(드래프트 제외)의 zip으로 실행 중인 .app을 교체한다. */
object Updater {
    private const val REPO = "tmp-it-play/dev-struct"

    /** jpackage로 만든 .app에서 실행 중일 때만 값이 있다. */
    val appBundle: Path? =
        System
            .getProperty("jpackage.app-path")
            ?.let { Path.of(it).parent?.parent?.parent }
            ?.takeIf { it.toString().endsWith(".app") }

    fun latestNewer(): Release? {
        val json = GitHub.get("repos/$REPO/releases/latest") ?: return null
        val tag = json.string("tag_name")
        val asset =
            json["assets"]
                ?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it.string("name").endsWith(".dmg") } ?: return null
        return Release(tag, asset.string("name"), asset.string("browser_download_url")).takeIf {
            isNewer(tag, APP_VERSION)
        }
    }

    fun install(release: Release, onProgress: (Float) -> Unit = {}): String? {
        val bundle = appBundle ?: return "앱 번들(.app)로 실행할 때만 업데이트할 수 있어요"
        val tmp = Files.createTempDirectory("devstruct-update")
        val dmg = tmp.resolve(release.asset)
        if (!runCatching {
                GitHub.download(release.url, dmg, onProgress)
            }.getOrDefault(false)
        ) {
            return "다운로드 실패: ${release.url}"
        }
        val mount = tmp.resolve("mnt")
        // hdiutil attach는 deprecated. diskutil image가 없는 구버전 macOS에서만 hdiutil을 쓴다
        val attach =
            runCommand(
                "diskutil",
                "image",
                "attach",
                "--readOnly",
                "--nobrowse",
                "--mountPoint",
                mount.toString(),
                dmg.toString(),
            )
                .takeIf { it.ok }
                ?: runCommand(
                    "hdiutil",
                    "attach",
                    "-nobrowse",
                    "-readonly",
                    "-mountpoint",
                    mount.toString(),
                    dmg.toString(),
                )
        if (!attach.ok) return "dmg 열기 실패: ${attach.output}"
        val newApp = tmp.resolve("DevStruct.app")
        val copy = runCommand("ditto", mount.resolve("DevStruct.app").toString(), newApp.toString())
        runCommand("diskutil", "eject", mount.toString())
        if (!copy.ok || !newApp.isDirectory()) return "앱 복사 실패: ${copy.output}"

        // 종료를 기다렸다가 교체한다. 실패하면 기존 앱을 되돌린다
        val pid = ProcessHandle.current().pid()
        val script =
            """
            while kill -0 $pid 2>/dev/null; do sleep 1; done
            mv "$bundle" "$bundle.old" || exit 1
            if mv "$newApp" "$bundle"; then rm -rf "$bundle.old"; else mv "$bundle.old" "$bundle"; fi
            open "$bundle"
            """.trimIndent()
        ProcessBuilder("/bin/sh", "-c", script).start()
        exitProcess(0)
    }
}

fun isNewer(tag: String, current: String): Boolean {
    fun parts(v: String) = v.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(tag)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (diff != 0) return diff > 0
    }
    return false
}
