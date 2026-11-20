package team.themoment.dev_struct

data class GitHubRepo(val id: Long, val ref: RepoRef, val fork: Boolean)

/** gh CLI의 인증을 그대로 쓴다. 이전/이름 변경된 레포는 gh가 리다이렉트를 따라가 새 full_name을 돌려준다. */
object GitHub {
    fun byId(id: Long) = fetch("repositories/$id")

    fun byRef(ref: RepoRef) = fetch("repos/${ref.owner}/${ref.name}")

    private fun fetch(endpoint: String): GitHubRepo? {
        val result = runCommand("gh", "api", endpoint, "--jq", """"\(.id)\t\(.full_name)\t\(.fork)"""")
        if (!result.ok) return null
        val fields = result.output.lines().last().split('\t')
        if (fields.size != 3) return null
        val (owner, name) = fields[1].split('/', limit = 2)
        return GitHubRepo(fields[0].toLong(), RepoRef(owner, name), fields[2].toBoolean())
    }
}
