package team.themoment.dev_struct

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

const val FORK_DIR = "OpenSource"

data class RepoPlan(
    val dir: Path,
    val repo: GitHubRepo,
    val storeId: Boolean,
    val newRemote: String?,
    val target: Path,
) {
    // APFS는 대소문자를 구분하지 않으므로 대소문자만 다르면 옮기지 않는다
    val needsMove get() = !dir.toString().equals(target.toString(), ignoreCase = true)
}

data class ScanResult(val plans: List<RepoPlan>, val limitedUntil: Long?)

fun targetPath(root: Path, repo: GitHubRepo): Path =
    root.resolve(if (repo.fork) FORK_DIR else repo.ref.owner).resolve(repo.ref.name)

/** root/{owner}/{repo} 구조를 유지한다. remote가 없거나 GitHub가 아닌 레포는 건드리지 않는다. */
class Organizer(private val root: Path, private val log: (String) -> Unit) {
    /** 부수효과 없이 계획만 만든다. */
    /** 요청 한도에 걸리면 그때까지 만든 계획과 한도 초기화 시각을 돌려준다. */
    fun scan(): ScanResult {
        val plans = mutableListOf<RepoPlan>()
        for (dir in repoDirs()) {
            try {
                plan(dir)?.let(plans::add)
            } catch (e: GitHubLimitException) {
                return ScanResult(plans, e.resetAt)
            }
        }
        return ScanResult(plans, null)
    }

    private fun repoDirs(): List<Path> = root
        .listDirectoryEntries()
        .filter { it.isDirectory() && !it.name.startsWith(".") }
        // 루트 바로 아래에 만든 레포도 remote가 생기면 제자리로 옮긴다
        .flatMap { dir -> if (dir.isRepo()) listOf(dir) else dir.listDirectoryEntries().filter { it.isRepo() } }

    private fun Path.isRepo() = resolve(".git").isDirectory()

    private fun plan(dir: Path): RepoPlan? {
        val remote = git(dir, "remote", "get-url", "origin") ?: return null
        val ref = parseGitHubRemote(remote) ?: return null
        // remote 이름으로 먼저 찾는다(이전·이름 변경은 리다이렉트로 따라간다). 리다이렉트가 끊겼을 때만 저장된 ID로 찾는다.
        // ID를 먼저 쓰면 사용자가 remote를 다른 레포로 바꿨을 때 예전 레포로 되돌려버린다.
        val storedId = git(dir, "config", "--local", "devstruct.repoId")?.toLongOrNull()
        val repo =
            GitHub.byRef(ref) ?: storedId?.let(GitHub::byId) ?: run {
                log("GitHub에서 찾을 수 없어 건너뜀(비공개 레포는 로그인 필요): ${ref.fullName}")
                return null
            }
        val newRemote = if (repo.ref.sameAs(ref)) null else rewriteGitHubRemote(remote, repo.ref)
        return RepoPlan(dir, repo, storedId != repo.id, newRemote, targetPath(root, repo))
    }

    /** remote 교체와 ID 기록. IDE와 무관하므로 즉시 적용한다. */
    fun applyGit(plan: RepoPlan) {
        if (plan.storeId) git(plan.dir, "config", "--local", "devstruct.repoId", plan.repo.id.toString())
        plan.newRemote?.let {
            git(plan.dir, "remote", "set-url", "origin", it)
            log("remote 변경: ${plan.dir.name} → $it")
        }
    }

    /** IDE가 모두 꺼져 있을 때만 호출해야 한다. */
    fun move(plan: RepoPlan): Boolean {
        if (Files.exists(plan.target)) {
            log("이동 대상이 이미 있어 건너뜀: ${plan.target}")
            return false
        }
        Files.createDirectories(plan.target.parent)
        Files.move(plan.dir, plan.target)
        JetBrains.relink(plan.dir, plan.target)
        removeIfEmpty(plan.dir.parent)
        log("이동: ${root.relativize(plan.dir)} → ${root.relativize(plan.target)}")
        return true
    }

    private fun removeIfEmpty(dir: Path) {
        if (dir == root) return
        if (dir.listDirectoryEntries().all { it.name == ".DS_Store" }) dir.toFile().deleteRecursively()
    }

    private fun git(dir: Path, vararg args: String): String? =
        runCommand("git", *args, dir = dir.toFile()).takeIf { it.ok }?.output
}
