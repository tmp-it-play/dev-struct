package team.themoment.dev_struct

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** 주기 스캔 + 이동 대기열. 상태는 Main(Swing) 스레드에서만 바꾼다. */
class DevStruct(private val scope: CoroutineScope, private val notify: (String) -> Unit) {
    var pending by mutableStateOf<List<RepoPlan>>(emptyList())
        private set
    var approved by mutableStateOf<Set<Path>>(emptySet())
        private set
    var blockingIdes by mutableStateOf<List<String>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set
    var nextScanAt by mutableStateOf(0L)
        private set

    /** 사용자가 알아야 할 문제만 담는다 */
    var problem by mutableStateOf<String?>(null)
        private set
    var update by mutableStateOf<Release?>(null)
        private set
    var loggedIn by mutableStateOf(GitHubAuth.token() != null)
        private set
    var deviceCode by mutableStateOf<DeviceCode?>(null)
        private set
    var autoMove by mutableStateOf(Settings.autoMove)
        private set

    private val scanRequest = Channel<Unit>(Channel.CONFLATED)

    fun start() = scope.launch {
        while (isActive) {
            if (System.currentTimeMillis() >= nextScanAt) {
                nextScanAt = System.currentTimeMillis() + Settings.intervalMinutes.minutes.inWholeMilliseconds
                scan()
            }
            processMoves()
            // IDE가 꺼지는 순간을 잡기 위해 대기 중엔 짧게 다시 확인한다
            if (withTimeoutOrNull(30.seconds) { scanRequest.receive() } != null) nextScanAt = 0
        }
    }

    fun installUpdate() {
        val release = update ?: return
        scope.launch {
            withContext(Dispatchers.IO) { Updater.install(release) }?.let {
                log(it)
                problem = "업데이트하지 못했어요"
            }
        }
    }

    fun login() {
        scope.launch {
            val code = withContext(Dispatchers.IO) { GitHubAuth.requestCode() }
            if (code == null) {
                problem = "GitHub에 연결하지 못했어요"
                return@launch
            }
            deviceCode = code
            runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(code.verificationUri)) }
            val error = withContext(Dispatchers.IO) { GitHubAuth.awaitToken(code) }
            deviceCode = null
            loggedIn = GitHubAuth.token() != null
            error?.let(::log)
            if (loggedIn) scanNow()
        }
    }

    fun logout() {
        GitHubAuth.logout()
        loggedIn = false
    }

    fun scanNow() {
        scanRequest.trySend(Unit)
    }

    fun updateAutoMove(value: Boolean) {
        autoMove = value
        Settings.autoMove = value
        scanNow()
    }

    fun approve(plan: RepoPlan) {
        approved += plan.dir
        scanNow()
    }

    fun approveAll() {
        approved += pending.map { it.dir }
        scanNow()
    }

    private suspend fun scan() {
        scanning = true
        try {
            val organizer = Organizer(Path.of(Settings.root), ::log)
            val result = withContext(Dispatchers.IO) { organizer.scan() }
            val plans = result.plans
            withContext(Dispatchers.IO) { plans.forEach(organizer::applyGit) }
            val moves = plans.filter { it.needsMove }
            val isNew = moves.any { m -> pending.none { it.dir == m.dir } }
            // 한도에 걸려 중간에 멈췄으면 아직 못 본 레포의 기존 대기 항목은 남긴다
            val scanned = plans.map { it.dir }.toSet()
            pending = if (result.limitedUntil == null) moves else pending.filter { it.dir !in scanned } + moves
            approved = approved.filterTo(mutableSetOf()) { dir -> pending.any { it.dir == dir } }
            log("스캔 완료: 레포 ${plans.size}개, 이동 필요 ${moves.size}개")
            loggedIn = GitHubAuth.token() != null
            val limitedUntil =
                result.limitedUntil ?: runCatching {
                    val latest = withContext(Dispatchers.IO) { Updater.latestNewer() }
                    if (latest != null && latest != update) notify("새 버전 ${latest.tag}을 설치할 수 있어요")
                    update = latest
                    null
                }.getOrElse { (it as? GitHubLimitException)?.resetAt ?: throw it }
            problem = limitedUntil?.let(::limitMessage)
            // 한도가 풀리기 전에는 다시 확인하지 않는다
            if (limitedUntil != null) nextScanAt = maxOf(nextScanAt, limitedUntil + 5_000)
            if (isNew && !autoMove) notify("옮길 레포가 ${moves.size}개 있어요")
        } catch (e: Exception) {
            log("스캔 실패: $e")
            problem = "폴더를 확인하지 못했어요"
        } finally {
            scanning = false
        }
    }

    private fun limitMessage(resetAt: Long): String {
        val time = java.time.Instant.ofEpochMilli(resetAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        val at = "%d:%02d".format(time.hour, time.minute)
        val base = "GitHub 요청 한도를 다 써서 $at 에 다시 확인해요"
        return if (loggedIn) base else "$base · 로그인하면 한도가 늘어나요"
    }

    private suspend fun processMoves() {
        val ready = pending.filter { autoMove || it.dir in approved }
        if (ready.isEmpty()) return
        blockingIdes = withContext(Dispatchers.IO) { JetBrains.runningIdes() }
        if (blockingIdes.isNotEmpty()) return

        val organizer = Organizer(Path.of(Settings.root), ::log)
        val done =
            ready.filter { plan ->
                withContext(Dispatchers.IO) {
                    runCatching { organizer.move(plan) }.getOrElse {
                        log("이동 실패: ${plan.dir} ($it)")
                        false
                    }
                }
            }
        pending = pending - ready.toSet()
        approved = approved - ready.map { it.dir }.toSet()
        if (done.isNotEmpty()) notify("레포 ${done.size}개를 옮겼어요")
    }

    private fun log(message: String) {
        runCatching {
            // 1MB를 넘으면 이전 로그 한 개만 남긴다
            if (Files.exists(LOG_FILE) && Files.size(LOG_FILE) > 1_000_000) {
                Files.move(LOG_FILE, LOG_FILE.resolveSibling("DevStruct.old.log"), StandardCopyOption.REPLACE_EXISTING)
            }
            Files.writeString(LOG_FILE, "${LocalDateTime.now()} $message\n", CREATE, APPEND)
        }
    }

    private companion object {
        val LOG_FILE: Path = Path.of(System.getProperty("user.home"), "Library/Logs/DevStruct.log")
    }
}
