package team.themoment.dev_struct

data class RepoRef(val owner: String, val name: String) {
    val fullName get() = "$owner/$name"

    // GitHub 이름은 대소문자를 구분하지 않는다
    fun sameAs(other: RepoRef) = fullName.equals(other.fullName, ignoreCase = true)
}

private val GITHUB_REMOTE = Regex("""^(.*github\.com[:/])([^/]+)/([^/]+?)(\.git)?/?$""")

fun parseGitHubRemote(url: String): RepoRef? =
    GITHUB_REMOTE.matchEntire(url.trim())?.let { RepoRef(it.groupValues[2], it.groupValues[3]) }

/** 프로토콜(https/ssh)과 .git 접미사는 유지하고 owner/name만 바꾼다. */
fun rewriteGitHubRemote(url: String, to: RepoRef): String? = GITHUB_REMOTE.matchEntire(url.trim())?.let {
    "${it.groupValues[1]}${to.owner}/${to.name}${it.groupValues[4]}"
}
