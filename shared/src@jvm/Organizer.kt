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

fun targetPath(root: Path, repo: GitHubRepo): Path =
    root.resolve(if (repo.fork) FORK_DIR else repo.ref.owner).resolve(repo.ref.name)

/** root/{owner}/{repo} 구조를 유지한다. remote가 없거나 GitHub가 아닌 레포는 건드리지 않는다. */
class Organizer(private val root: Path, private val log: (String) -> Unit) {
    /** 부수효과 없이 계획만 만든다. */
    fun scan(): List<RepoPlan> = repoDirs().mapNotNull(::plan)

    private fun repoDirs(): List<Path> = root
        .listDirectoryEntries()
        .filter { it.isDirectory() && !it.name.startsWith(".") }
        .flatMap { owner -> owner.listDirectoryEntries().filter { it.resolve(".git").isDirectory() } }

    private fun plan(dir: Path): RepoPlan? {
        val remote = git(dir, "remote", "get-url", "origin") ?: return null
        val ref = parseGitHubRemote(remote) ?: return null
        // 레포 ID는 이전·이름 변경에도 바뀌지 않아 리다이렉트가 끊겨도 추적된다
        val storedId = git(dir, "config", "--local", "devstruct.repoId")?.toLongOrNull()
        val repo =
            storedId?.let(GitHub::byId) ?: GitHub.byRef(ref) ?: run {
                log("GitHub에서 찾을 수 없어 건너뜀: ${ref.fullName} ($dir)")
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
