package team.themoment.dev_struct

import java.io.File

// Finder/LaunchAgent로 실행되면 Homebrew PATH가 없다
private val searchPath =
    (System.getenv("PATH").orEmpty().split(':') + listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"))
        .filter { it.isNotBlank() }
        .distinct()

private fun resolve(cmd: String) = searchPath.map { File(it, cmd) }.firstOrNull { it.canExecute() }?.path ?: cmd

data class CmdResult(val exitCode: Int, val output: String) {
    val ok get() = exitCode == 0
}

fun runCommand(vararg cmd: String, dir: File? = null): CmdResult {
    val process =
        ProcessBuilder(resolve(cmd[0]), *cmd.drop(1).toTypedArray())
            .directory(dir)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    return CmdResult(process.waitFor(), output.trim())
}
