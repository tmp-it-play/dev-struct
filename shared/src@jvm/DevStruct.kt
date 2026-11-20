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

    /** 사용자가 지금 위치에 고정한 저장소(마지막 확인 기준) */
    var pinned by mutableStateOf<List<RepoPlan>>(emptyList())
        private set

    /** 수동 업데이트 확인 결과("최신 버전이에요" 등) */
    var updateStatus by mutableStateOf<String?>(null)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set

    /** 업데이트 내려받기 진행률(0..1). 설치 중이 아니면 null */
    var updateProgress by mutableStateOf<Float?>(null)
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
        if (updateProgress != null) return
        updateProgress = 0f
        scope.launch {
            val error =
                withContext(Dispatchers.IO) {
                    // 진행률은 Main에서만 바꾸고, 퍼센트가 바뀔 때만 알린다
                    var lastPercent = -1
                    Updater.install(release) { progress ->
                        val percent = (progress * 100).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            scope.launch { updateProgress = progress }
                        }
                    }
                }
            updateProgress = null
            error?.let {
                log(it)
                updateStatus = "업데이트하지 못했어요"
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
            // 브라우저에 붙여넣기만 하면 되도록 코드를 복사해 둔다
            runCatching {
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                    java.awt.datatransfer.StringSelection(code.userCode),
                    null,
                )
            }
            notify("로그인 코드 ${code.userCode}를 복사했어요. 열린 브라우저에 붙여넣으세요")
            runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(code.verificationUri)) }
            val error = withContext(Dispatchers.IO) { GitHubAuth.awaitToken(code) }
            deviceCode = null
            loggedIn = GitHubAuth.token() != null
            error?.let(::log)
            if (loggedIn) {
                notify("GitHub에 로그인했어요")
                scanNow()
            }
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

    fun checkForUpdate() {
        if (checkingUpdate) return
        scope.launch {
            checkingUpdate = true
            updateStatus = null
            val limit = checkUpdate()
            updateStatus =
                when {
                    limit != null -> limitMessage(limit)
                    update == null -> "최신 버전이에요"
                    else -> null
                }
            checkingUpdate = false
        }
    }

    /** 지금 위치에 고정하면 다시는 옮기지 않는다 */
    fun pin(plan: RepoPlan) {
        scope.launch {
            withContext(Dispatchers.IO) { Organizer(Path.of(Settings.root), ::log).setPinned(plan.dir, true) }
            pending = pending.filter { it.dir != plan.dir }
            approved = approved - plan.dir
            pinned = (pinned + plan.copy(pinned = true)).sortedBy { it.dir }
        }
    }

    fun unpin(plan: RepoPlan) {
        scope.launch {
            withContext(Dispatchers.IO) { Organizer(Path.of(Settings.root), ::log).setPinned(plan.dir, false) }
            pinned = pinned.filter { it.dir != plan.dir }
            scanNow()
        }
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
            // 저장소 확인이 한도에 걸려도 업데이트는 알 수 있게 먼저 확인한다(ETag로 304면 한도를 쓰지 않는다)
            val updateLimit = checkUpdate()
            val organizer = Organizer(Path.of(Settings.root), ::log)
            val result = withContext(Dispatchers.IO) { organizer.scan() }
            val plans = result.plans
            withContext(Dispatchers.IO) { plans.forEach(organizer::applyGit) }
            val moves = plans.filter { it.needsMove }
            pinned = plans.filter { it.pinned }.sortedBy { it.dir }
            val isNew = moves.any { m -> pending.none { it.dir == m.dir } }
            // 한도에 걸려 중간에 멈췄으면 아직 못 본 저장소의 기존 대기 항목은 남긴다
            val scanned = plans.map { it.dir }.toSet()
            pending = if (result.limitedUntil == null) moves else pending.filter { it.dir !in scanned } + moves
            approved = approved.filterTo(mutableSetOf()) { dir -> pending.any { it.dir == dir } }
            log("스캔 완료: 저장소 ${plans.size}개, 이동 필요 ${moves.size}개")
            loggedIn = GitHubAuth.token() != null
            val limitedUntil = result.limitedUntil ?: updateLimit
            problem = limitedUntil?.let(::limitMessage)
            // 한도가 풀리기 전에는 다시 확인하지 않는다
            if (limitedUntil != null) nextScanAt = maxOf(nextScanAt, limitedUntil + 5_000)
            if (isNew && !autoMove) notify("옮길 저장소가 ${moves.size}개 있어요")
        } catch (e: Exception) {
            log("스캔 실패: $e")
            problem = "폴더를 확인하지 못했어요"
        } finally {
            scanning = false
        }
    }

    /** 한도에 걸렸으면 초기화 시각을 돌려준다 */
    private suspend fun checkUpdate(): Long? = try {
        val latest = withContext(Dispatchers.IO) { Updater.latestNewer() }
        if (latest != null && latest != update) notify("새 버전 ${latest.tag}을 설치할 수 있어요")
        update = latest
        null
    } catch (e: GitHubLimitException) {
        e.resetAt
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
        blockingIdes = if (Settings.jetbrains) withContext(Dispatchers.IO) { JetBrains.runningIdes() } else emptyList()
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
        if (done.isNotEmpty()) notify("저장소 ${done.size}개를 옮겼어요")
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
