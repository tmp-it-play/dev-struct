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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class Page(val label: String) {
    Repos("정리"),
    Settings("설정"),
}

/** 주기 스캔 + 이동 대기열. 상태는 Main(Swing) 스레드에서만 바꾼다. */
class DevStruct(private val scope: CoroutineScope, private val notify: (String, Page) -> Unit) {
    var page by mutableStateOf(Page.Repos)

    var pending by mutableStateOf<List<RepoPlan>>(emptyList())
        private set
    var approved by mutableStateOf<Set<Path>>(emptySet())
        private set
    var blockingIdes by mutableStateOf<List<String>>(emptyList())
        private set

    /** 실행 중인 에이전트 CLI. 옮기며 바꾼 경로를 덮어쓸 수 있어 경고한다 */
    var runningAgents by mutableStateOf<List<String>>(emptyList())
        private set

    /** 터미널·에이전트가 작업 폴더로 쓰고 있어 미룬 저장소 */
    var busyRepos by mutableStateOf<Set<Path>>(emptySet())
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
    private var accountToken by mutableStateOf(GitHubAuth.token())
    val loggedIn get() = accountToken != null
    var githubUsername by mutableStateOf<String?>(null)
        private set
    var deviceCode by mutableStateOf<DeviceCode?>(null)
        private set
    var autoMove by mutableStateOf(Settings.autoMove)
        private set

    /** 사용자가 지금 위치에 고정한 저장소(마지막 확인 기준) */
    var pinned by mutableStateOf<List<RepoPlan>>(emptyList())
        private set

    /** 루트 밖에서 찾은 저장소(승인하면 가져온다)와, 그중 지금 위치에 두기로 한 저장소 */
    var discovered by mutableStateOf<List<Discovered>>(emptyList())
        private set
    var keptElsewhere by mutableStateOf<List<Discovered>>(emptyList())
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

    private val discoverRequest = Channel<Unit>(Channel.CONFLATED)

    // 홈 폴더 찾기는 한 흐름에서만 천천히 돈다
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val discoveryDispatcher = Dispatchers.IO.limitedParallelism(1)

    fun start() {
        scope.launch { discoverLoop() }
        scanLoop()
    }

    /** 시작 1분 뒤 한 바퀴 돌고, 이후 6시간마다(설정을 바꾸면 바로) 다시 돈다 */
    private suspend fun discoverLoop() {
        withTimeoutOrNull(60.seconds) { discoverRequest.receive() }
        while (true) {
            if (Settings.discover) {
                val home = Path.of(System.getProperty("user.home"))
                val found = withContext(discoveryDispatcher) { Discovery(home, Path.of(Settings.root)).scan() }
                val previous = discovered.map { it.dir }.toSet()
                discovered = if (Settings.discover) found.filter { !it.pinned }.sortedBy { it.dir } else emptyList()
                keptElsewhere = found.filter { it.pinned }.sortedBy { it.dir }
                val newCount = discovered.count { it.dir !in previous }
                if (newCount > 0) notify("홈 폴더에서 저장소 ${newCount}개를 찾았어요", Page.Repos)
                log("홈 폴더 찾기 완료: 루트 밖 저장소 ${found.size}개")
            } else {
                discovered = emptyList()
            }
            withTimeoutOrNull(6.hours) { discoverRequest.receive() }
        }
    }

    fun discoverNow() {
        discoverRequest.trySend(Unit)
    }

    /** 루트 밖 저장소를 GitHub에서 확인해 루트의 제자리로 옮기도록 대기열에 넣는다(IDE 대기 규칙은 같다) */
    fun importRepo(found: Discovered) {
        scope.launch {
            val organizer = Organizer(Path.of(Settings.root), ::log)
            val plan =
                try {
                    withContext(Dispatchers.IO) { organizer.plan(found.dir)?.also(organizer::applyGit) }
                } catch (e: GitHubLimitException) {
                    problem = limitMessage(e.resetAt)
                    return@launch
                }
            if (plan == null) {
                problem = "GitHub에 ${found.ref.fullName}이(가) 없어요"
                return@launch
            }
            discovered = discovered.filter { it.dir != found.dir }
            if (!plan.needsMove) return@launch
            pending = pending.filter { it.dir != plan.dir } + plan
            approved = approved.plusElement(plan.dir)
            scanNow()
        }
    }

    /** 루트 밖 저장소를 지금 위치에 고정해 다시 묻지 않는다 */
    fun keepHere(found: Discovered) {
        scope.launch {
            withContext(Dispatchers.IO) { Organizer(Path.of(Settings.root), ::log).setPinned(found.dir, true) }
            discovered = discovered.filter { it.dir != found.dir }
            keptElsewhere = (keptElsewhere + found.copy(pinned = true)).sortedBy { it.dir }
        }
    }

    fun unkeep(found: Discovered) {
        scope.launch {
            withContext(Dispatchers.IO) { Organizer(Path.of(Settings.root), ::log).setPinned(found.dir, false) }
            keptElsewhere = keptElsewhere.filter { it.dir != found.dir }
            discovered = (discovered + found.copy(pinned = false)).sortedBy { it.dir }
        }
    }

    private fun scanLoop() = scope.launch {
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
                problem = "GitHub 연결 실패"
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
            notify("로그인 코드 ${code.userCode}를 복사했어요. 브라우저에 붙여넣으세요", Page.Repos)
            runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(code.verificationUri)) }
            val error = withContext(Dispatchers.IO) { GitHubAuth.awaitToken(code) }
            deviceCode = null
            syncAccount()
            error?.let(::log)
            if (loggedIn) {
                notify("GitHub에 로그인했어요", Page.Repos)
                scanNow()
            }
        }
    }

    fun logout() {
        GitHubAuth.logout()
        syncAccount()
    }

    private fun syncAccount() {
        val token = GitHubAuth.token()
        if (accountToken != token) {
            accountToken = token
            githubUsername = null
        }
    }

    suspend fun refreshGitHubUsername() {
        val token = accountToken ?: return
        val username = withContext(Dispatchers.IO) {
            try {
                GitHub.get("user")?.string("login")?.takeIf { it.isNotBlank() }
            } catch (_: GitHubLimitException) {
                null
            }
        }
        syncAccount()
        if (accountToken == token && username != null) githubUsername = username
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
            approved = approved.minusElement(plan.dir)
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
        // Path는 Iterable<Path>라 `+ path`가 경로 조각들을 더하므로 plusElement로 한 개만 더한다
        approved = approved.plusElement(plan.dir)
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
            // 루트 밖에서 가져오기로 한 항목은 루트 확인 결과와 상관없이 남긴다
            val root = Path.of(Settings.root)
            val external = pending.filter { !it.dir.startsWith(root) }
            pending =
                if (result.limitedUntil == null) {
                    moves + external
                } else {
                    pending.filter { it.dir !in scanned } + moves
                }
            approved = approved.filterTo(mutableSetOf()) { dir -> pending.any { it.dir == dir } }
            log("스캔 완료: 저장소 ${plans.size}개, 이동 필요 ${moves.size}개")
            syncAccount()
            val limitedUntil = result.limitedUntil ?: updateLimit
            problem = limitedUntil?.let(::limitMessage)
            // 한도가 풀리기 전에는 다시 확인하지 않는다
            if (limitedUntil != null) nextScanAt = maxOf(nextScanAt, limitedUntil + 5_000)
            if (isNew && !autoMove) notify("옮길 저장소가 ${moves.size}개 있어요", Page.Repos)
        } catch (e: Exception) {
            log("스캔 실패: $e")
            problem = "폴더 확인 실패"
        } finally {
            scanning = false
        }
    }

    /** 한도에 걸렸으면 초기화 시각을 돌려준다 */
    private suspend fun checkUpdate(): Long? = try {
        val latest = withContext(Dispatchers.IO) { Updater.latestNewer() }
        if (updateProgress == null) {
            if (latest != null && latest != update) notify("새 버전 ${latest.tag}을 설치할 수 있어요", Page.Settings)
            update = latest
        }
        null
    } catch (e: GitHubLimitException) {
        e.resetAt
    }

    private fun limitMessage(resetAt: Long): String {
        val time = java.time.Instant.ofEpochMilli(resetAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        return "GitHub 요청 한도 초과 · %d:%02d에 다시 확인".format(time.hour, time.minute)
    }

    private suspend fun processMoves() {
        runningAgents =
            if (pending.isNotEmpty() &&
                Settings.agentTools
            ) {
                withContext(Dispatchers.IO) { DevTools.runningAgents() }
            } else {
                emptyList()
            }
        val ready = pending.filter { autoMove || it.dir in approved }
        if (ready.isEmpty()) {
            // 기다릴 항목이 없으면 이전 대기 사유도 지운다
            blockingIdes = emptyList()
            busyRepos = emptySet()
            return
        }
        blockingIdes = if (Settings.devTools) withContext(Dispatchers.IO) { DevTools.running() } else emptyList()
        if (blockingIdes.isNotEmpty()) return

        // 저장소 안에서 실행 중인 프로세스가 있으면 그 저장소만 미룬다(CLI 에이전트는 늘 켜져 있어 전체를 막지 않는다)
        val busy = withContext(Dispatchers.IO) { DevTools.busyDirs() }
        busyRepos = ready.filter { plan -> busy.any { it.startsWith(plan.dir) } }.map { it.dir }.toSet()
        val movable = ready.filter { it.dir !in busyRepos }
        if (movable.isEmpty()) return

        val organizer = Organizer(Path.of(Settings.root), ::log)
        val done =
            movable.filter { plan ->
                withContext(Dispatchers.IO) {
                    runCatching { organizer.move(plan) }.getOrElse {
                        log("이동 실패: ${plan.dir} ($it)")
                        false
                    }
                }
            }
        pending = pending - movable.toSet()
        approved = approved - movable.map { it.dir }.toSet()
        if (done.isNotEmpty()) notify("저장소 ${done.size}개를 옮겼어요", Page.Repos)
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
