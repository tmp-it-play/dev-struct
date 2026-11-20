package team.themoment.dev_struct

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * 프로젝트 경로를 기억하는 개발 도구(JetBrains IDE, Android Studio, VS Code 계열, Orca, Paseo, herdr, JetBrains Air, 에이전트 CLI).
 * 대부분 종료할 때 상태를 다시 쓰므로, 실행 중이면 이동을 미루고 모두 꺼진 뒤에만 경로를 바꾼다.
 */
object DevTools {
    private val home = System.getProperty("user.home")
    private val appSupport = Path.of(home, "Library/Application Support")

    private val jetbrainsBinaries =
        setOf(
            "idea", "webstorm", "goland", "pycharm", "datagrip", "clion", "rider", "rubymine",
            "phpstorm", "rustrover", "dataspell", "aqua", "writerside",
            // Android Studio
            "studio",
        )

    // 앱 번들 이름 → 실행 파일 이름(null이면 번들 안의 아무 실행 파일)
    private val apps =
        mapOf(
            "Visual Studio Code" to "Code",
            // VS Code 기반 에디터
            "Cursor" to null,
            "Windsurf" to null,
            "Antigravity" to null,
            "Orca" to "Orca",
            // 백그라운드 서버(Paseo Daemon)도 같은 실행 파일이다
            "Paseo" to "Paseo",
            "Herdr" to "Herdr",
            // Air는 실행 파일 이름이 확인되지 않았다
            "Air" to null,
        )

    // 터미널 도구 프로세스 이름
    private val clis = mapOf("herdr" to "herdr", "herdr-core" to "herdr")

    /** 실행 중인 도구 이름 목록 */
    fun running(): List<String> = ProcessHandle
        .allProcesses()
        .toList()
        .mapNotNull { it.info().command().orElse(null) }
        .mapNotNull { command ->
            val binary = command.substringAfterLast('/')
            val bundle = command.substringBefore(".app/Contents/MacOS/", "").substringAfterLast('/')
            when {
                bundle.isNotEmpty() && binary in jetbrainsBinaries -> bundle
                bundle in apps && apps[bundle].let { it == null || it == binary } -> bundle
                ".app/" !in command -> clis[binary]
                else -> null
            }
        }.distinct()

    /** 켜진 연동의 도구들이 기억하는 경로를 모두 새 위치로 바꾼다. 원본은 .devstruct.bak로 남긴다. */
    fun relink(from: Path, to: Path, ides: Boolean, agents: Boolean) {
        val (f, t) = from.toString() to to.toString()
        if (ides) {
            ideTextFiles().forEach { relinkText(it, f, t) }
            ideSqliteFiles().forEach { relinkSqlite(it, f, t) }
        }
        if (agents) {
            agentTextFiles().forEach { relinkText(it, f, t) }
            agentSqliteFiles().forEach { relinkSqlite(it, f, t) }
            runCatching { relinkClaudeProjects(f, t) }
        }
    }

    private fun ideTextFiles(): List<Path> {
        // JetBrains IDE(Android Studio는 Google 아래). Rider는 recentSolutions.xml을 쓴다
        val jetbrains =
            listOf("JetBrains", "Google")
                .map { appSupport.resolve(it) }
                .flatMap { it.children() }
                .flatMap { product ->
                    listOf("recentProjects.xml", "recentSolutions.xml", "trusted-paths.xml").map {
                        product.resolve("options/$it")
                    }
                }
        val vscode =
            vscodeUsers().flatMap { user ->
                listOf(user.resolve("globalStorage/storage.json")) +
                    user.resolve("workspaceStorage").children().map { it.resolve("workspace.json") }
            }
        val paseoHome = System.getenv("PASEO_HOME")?.let(Path::of) ?: Path.of(home, ".paseo")
        val paseo =
            listOf(paseoHome.resolve("projects/projects.json"), paseoHome.resolve("projects/workspaces.json")) +
                paseoHome.resolve("agents").children().flatMap {
                    it.children()
                }.filter { it.toString().endsWith(".json") }
        val config = System.getenv("XDG_CONFIG_HOME")?.let(Path::of) ?: Path.of(home, ".config")
        val herdr =
            listOf(config.resolve("herdr/session.json"), config.resolve("herdr-app/view.json")) +
                config.resolve("herdr/sessions").children().map { it.resolve("session.json") } +
                listOf("docs.json", "terminals.json").map { Path.of(home, ".local/state/herdr-core/default", it) }
        return (jetbrains + vscode + paseo + herdr).filter { it.isRegularFile() }
    }

    // 에이전트 CLI: 프로젝트별 설정·신뢰 목록을 경로로 기억한다
    private fun agentTextFiles(): List<Path> = listOf(
        ".claude.json",
        ".codex/config.toml",
        ".copilot/config.json",
        ".gemini/projects.json",
        ".gemini/trustedFolders.json",
    )
        .map { Path.of(home, it) }
        .filter { it.isRegularFile() }

    private fun ideSqliteFiles(): List<Path> = (
        vscodeUsers().map { it.resolve("globalStorage/state.vscdb") } +
            appSupport.resolve("Orca/profiles").children().map { it.resolve("profile-state.db") }
        ).filter { it.isRegularFile() }

    private fun agentSqliteFiles(): List<Path> {
        // Codex 스레드 목록(threads.cwd)
        val codex = Path.of(home, ".codex").children().filter {
            it.fileName.toString().matches(Regex("""state_\d+\.sqlite"""))
        }
        // Hermes Agent 세션(cwd). 프로필마다 state.db가 있다
        val hermesHome = System.getenv("HERMES_HOME")?.let(Path::of) ?: Path.of(home, ".hermes")
        val hermes =
            listOf(hermesHome.resolve("state.db")) +
                hermesHome.resolve("profiles").children().map { it.resolve("state.db") }
        return (codex + hermes).filter { it.isRegularFile() }
    }

    /**
     * Claude Code는 대화 기록·메모리를 경로를 이름으로 바꾼 폴더(~/.claude/projects/-Users-...)에 둔다.
     * 새 위치 이름의 폴더가 아직 없을 때만 옮긴다.
     */
    private fun relinkClaudeProjects(from: String, to: String) {
        val projects = Path.of(home, ".claude/projects")
        val encode = { path: String -> path.replace(Regex("[^A-Za-z0-9]"), "-") }
        val old = projects.resolve(encode(from))
        val new = projects.resolve(encode(to))
        if (Files.isDirectory(old) && !Files.exists(new)) Files.move(old, new)
    }

    // 에이전트 CLI: 실행 파일 이름, 또는 Node·Python으로 실행될 때 명령에 들어 있는 패키지 이름
    private val agentBinaries = mapOf(
        "claude" to "Claude Code",
        "codex" to "Codex",
        "copilot" to "GitHub Copilot CLI",
        "hermes" to "Hermes",
    )
    private val agentPackages =
        mapOf(
            "@anthropic-ai/claude-code" to "Claude Code",
            "@openai/codex" to "Codex",
            "@github/copilot" to "GitHub Copilot CLI",
            "gemini-cli" to "Gemini",
            "/bin/gemini" to "Gemini",
            "hermes" to "Hermes",
        )

    /**
     * 실행 중인 에이전트 CLI. 이들은 켜져 있는 동안 설정을 다시 써서, 옮기며 바꾼 경로를 예전 값으로 덮어쓸 수 있다.
     * 앱 번들·IDE 플러그인 안에서 뜬 같은 이름의 프로세스(예: Android Studio의 copilot)는 CLI 설정을 쓰지 않으므로 뺀다.
     */
    fun runningAgents(): List<String> = ProcessHandle
        .allProcesses()
        .toList()
        .mapNotNull { process ->
            val info = process.info()
            val command = info.command().orElse(null) ?: return@mapNotNull null
            if (".app/Contents" in command || "/plugins/" in command) return@mapNotNull null
            agentBinaries[command.substringAfterLast('/')]
                ?: info.arguments().orElse(emptyArray()).joinToString(" ").let { args ->
                    agentPackages.entries.firstOrNull { it.key in args }?.value
                }
        }.distinct()

    /** 이 폴더 안을 작업 폴더로 쓰는 프로세스(터미널, 에이전트 등)가 있는 경로들. 옮기면 그 프로세스가 망가진다 */
    fun busyDirs(): List<Path> = runCommand("lsof", "-a", "-d", "cwd", "-Fn")
        .output
        .lines()
        .filter { it.startsWith("n/") }
        .map { Path.of(it.removePrefix("n")) }

    private fun relinkText(file: Path, from: String, to: String) {
        val text = file.readText()
        val updated = replaceProjectPath(text, from, to, home)
        if (updated == text) return
        Files.copy(file, file.resolveSibling("${file.fileName}.devstruct.bak"), REPLACE_EXISTING)
        file.writeText(updated)
    }

    /** 모든 표의 문자열 칸에서 경로 경계(`"`, `/`)가 붙은 경우만 바꾼다(`/a/foo`가 `/a/foobar`를 건드리지 않게) */
    private fun relinkSqlite(db: Path, from: String, to: String) {
        fun sql(query: String) = runCommand("sqlite3", db.toString(), query)
        val q = { s: String -> "'" + s.replace("'", "''") + "'" }
        val tables = sql("select name from sqlite_master where type='table'").takeIf { it.ok } ?: return
        val updates =
            tables.output.lines().filter { it.isNotBlank() }.flatMap { table ->
                sql("select name, upper(type) from pragma_table_info(${q(table)})").output.lines().mapNotNull { line ->
                    val (column, type) = line.split('|').takeIf { it.size == 2 } ?: return@mapNotNull null
                    if (type.isNotEmpty() && "TEXT" !in type && "BLOB" !in type &&
                        "CHAR" !in type
                    ) {
                        return@mapNotNull null
                    }
                    val col = "\"${column.replace("\"", "\"\"")}\""
                    var value = "CAST($col AS TEXT)"
                    listOf("\"", "/").forEach { edge -> value = "replace($value, ${q(from + edge)}, ${q(to + edge)})" }
                    // BLOB으로 선언된 칸은 형식을 유지한다
                    if ("BLOB" in type) value = "CAST($value AS BLOB)"
                    "UPDATE \"${table.replace(
                        "\"",
                        "\"\"",
                    )}\" SET $col = $value WHERE instr(CAST($col AS TEXT), ${q(from)}) > 0;"
                }
            }
        if (updates.isEmpty()) return
        Files.copy(db, db.resolveSibling("${db.fileName}.devstruct.bak"), REPLACE_EXISTING)
        sql("BEGIN; ${updates.joinToString(" ")} COMMIT;")
    }

    // VS Code와 그 기반 에디터(Cursor, Windsurf)는 설정 구조가 같다
    private fun vscodeUsers() = listOf("Code", "Cursor", "Windsurf", "Antigravity").map {
        appSupport.resolve("$it/User")
    }

    private fun Path.children(): List<Path> = if (Files.isDirectory(this)) listDirectoryEntries() else emptyList()
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
