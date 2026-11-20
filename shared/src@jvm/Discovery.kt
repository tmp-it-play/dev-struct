package team.themoment.dev_struct

import kotlinx.coroutines.delay
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/** 루트 밖에서 찾은 GitHub 저장소. 소유자·이름은 remote 주소에서 읽은 값이다(GitHub에 묻지 않는다) */
data class Discovered(val dir: Path, val ref: RepoRef, val pinned: Boolean)

/**
 * 홈 폴더를 천천히 훑어 루트 밖 저장소를 찾는다. GitHub API는 쓰지 않는다.
 * 숨김 폴더(Homebrew·oh-my-zsh·nvm 등 도구 저장소가 대부분 여기 있다), ~/Library, 의존성 폴더, 심볼릭 링크는 건너뛴다.
 */
class Discovery(private val home: Path, private val root: Path) {
    private val skippedNames = setOf("node_modules", "Pods", "vendor", "venv", "build", "target", "dist", "DerivedData")
    private val skippedPaths = listOf("Library", "Applications").map { home.resolve(it) }

    /** 폴더 하나를 볼 때마다 쉬어 가며 끝까지 훑는다. 저장소를 찾으면 그 안으로는 들어가지 않는다 */
    suspend fun scan(pause: Long = 5): List<Discovered> {
        val found = mutableListOf<Discovered>()
        val queue = ArrayDeque(listOf(home))
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            val children = runCatching { Files.newDirectoryStream(dir).use { it.toList() } }.getOrDefault(emptyList())
            for (child in children) {
                if (!Files.isDirectory(child, NOFOLLOW_LINKS) || skip(child)) continue
                if (child.resolve(".git").isDirectory()) {
                    toDiscovered(child)?.let(found::add)
                } else {
                    queue.add(child)
                }
            }
            delay(pause)
        }
        return found
    }

    private fun skip(dir: Path) =
        dir.name.startsWith(".") || dir.name in skippedNames || dir == root || dir.startsWith(root) ||
            skippedPaths.any { dir == it }

    private fun toDiscovered(dir: Path): Discovered? {
        val remote =
            runCommand("git", "remote", "get-url", "origin", dir = dir.toFile()).takeIf { it.ok }?.output ?: return null
        val ref = parseGitHubRemote(remote) ?: return null
        val pinned =
            runCommand("git", "config", "--local", "--bool", "devstruct.pinned", dir = dir.toFile()).output == "true"
        return Discovered(dir, ref, pinned)
    }
}
