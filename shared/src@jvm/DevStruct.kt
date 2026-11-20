package team.themoment.dev_struct

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
    var lastScan by mutableStateOf<String?>(null)
        private set
    var update by mutableStateOf<Release?>(null)
        private set
    var autoMove by mutableStateOf(Settings.autoMove)
        private set
    val logs = mutableStateListOf<String>()

    private val scanRequest = Channel<Unit>(Channel.CONFLATED)

    fun start() = scope.launch {
        var nextScanAt = 0L
        while (isActive) {
            if (System.currentTimeMillis() >= nextScanAt) {
                scan()
                nextScanAt = System.currentTimeMillis() + Settings.intervalMinutes.minutes.inWholeMilliseconds
            }
            processMoves()
            // IDE가 꺼지는 순간을 잡기 위해 대기 중엔 짧게 다시 확인한다
            if (withTimeoutOrNull(30.seconds) { scanRequest.receive() } != null) nextScanAt = 0
        }
    }

    fun installUpdate() {
        val release = update ?: return
        scope.launch {
            log("${release.tag} 업데이트를 내려받는 중…")
            withContext(Dispatchers.IO) { Updater.install(release) }?.let(::log)
        }
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
            val plans = withContext(Dispatchers.IO) { organizer.scan().onEach(organizer::applyGit) }
            val moves = plans.filter { it.needsMove }
            val isNew = moves.any { m -> pending.none { it.dir == m.dir } }
            pending = moves
            approved = approved.filterTo(mutableSetOf()) { dir -> moves.any { it.dir == dir } }
            lastScan = LocalTime.now().format(TIME)
            log("스캔 완료: 레포 ${plans.size}개, 이동 필요 ${moves.size}개")
            val latest = withContext(Dispatchers.IO) { Updater.latestNewer() }
            if (latest != null && latest != update) notify("새 버전 ${latest.tag}이 나왔어요")
            update = latest
            if (isNew && !autoMove) notify("이동 승인이 필요한 레포가 ${moves.size}개 있어요")
        } catch (e: Exception) {
            log("스캔 실패: ${e.message}")
        } finally {
            scanning = false
        }
    }

    private suspend fun processMoves() {
        val ready = pending.filter { autoMove || it.dir in approved }
        if (ready.isEmpty()) return
        val ides = withContext(Dispatchers.IO) { JetBrains.runningIdes() }
        if (ides.isNotEmpty() && blockingIdes.isEmpty()) notify("${ides.joinToString()} 종료 후 ${ready.size}개 레포를 이동해요")
        blockingIdes = ides
        if (ides.isNotEmpty()) return

        val organizer = Organizer(Path.of(Settings.root), ::log)
        val done =
            ready.filter { plan ->
                withContext(Dispatchers.IO) {
                    runCatching { organizer.move(plan) }.getOrElse {
                        log("이동 실패: ${plan.dir} (${it.message})")
                        false
                    }
                }
            }
        pending = pending - ready.toSet()
        approved = approved - ready.map { it.dir }.toSet()
        if (done.isNotEmpty()) notify("${done.size}개 레포를 이동했어요")
    }

    // Organizer는 IO 스레드에서도 로그를 남기므로 Main으로 넘긴다
    private fun log(message: String) {
        scope.launch {
            logs.add(0, "[${LocalTime.now().format(TIME)}] $message")
            if (logs.size > 300) logs.removeRange(300, logs.size)
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
