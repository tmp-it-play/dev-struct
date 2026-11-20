package team.themoment.dev_struct

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

object JetBrains {
    private val home = System.getProperty("user.home")
    private val configRoot = Path.of(home, "Library/Application Support/JetBrains")
    private val ideBinaries =
        setOf(
            "idea",
            "webstorm",
            "goland",
            "pycharm",
            "datagrip",
            "clion",
            "rider",
            "rubymine",
            "phpstorm",
            "rustrover",
            "dataspell",
        )
    private val projectFiles = listOf("recentProjects.xml", "trusted-paths.xml")

    /** 실행 중인 JetBrains IDE 앱 이름 목록. IDE는 종료 시 설정 파일을 덮어쓰므로 비어 있을 때만 relink 해야 한다. */
    fun runningIdes(): List<String> = ProcessHandle
        .allProcesses()
        .toList()
        .mapNotNull { it.info().command().orElse(null) }
        .filter { ".app/Contents/MacOS/" in it && it.substringAfterLast('/') in ideBinaries }
        .map { it.substringBefore(".app/").substringAfterLast('/') }
        .distinct()

    /** 모든 IDE·버전의 최근 프로젝트/신뢰 경로를 새 위치로 바꾼다. 원본은 .devstruct.bak로 남긴다. */
    fun relink(from: Path, to: Path) {
        if (!Files.isDirectory(configRoot)) return
        configRoot
            .listDirectoryEntries()
            .flatMap { product -> projectFiles.map { product.resolve("options/$it") } }
            .filter { it.isRegularFile() }
            .forEach { file ->
                val text = file.readText()
                val updated = replaceProjectPath(text, from.toString(), to.toString(), home)
                if (updated != text) {
                    Files.copy(file, file.resolveSibling("${file.fileName}.devstruct.bak"), REPLACE_EXISTING)
                    file.writeText(updated)
                }
            }
    }
}

/** 절대경로, `$USER_HOME$`, `~` 표기를 모두 바꾸되 `/a/foo`가 `/a/foobar`를 건드리지 않게 경로 경계에서만 매칭한다. */
fun replaceProjectPath(text: String, from: String, to: String, home: String): String {
    fun forms(path: String) = if (path.startsWith("$home/")) {
        listOf(path, "\$USER_HOME\$" + path.removePrefix(home), "~" + path.removePrefix(home))
    } else {
        listOf(path)
    }
    return forms(from).zip(forms(to)).fold(text) { acc, (old, new) ->
        acc.replace(Regex(Regex.escape(old) + """(?=[/"\]<\s]|$)"""), Regex.escapeReplacement(new))
    }
}
