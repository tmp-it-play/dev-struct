package team.themoment.dev_struct

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration

data class GitHubRepo(val id: Long, val ref: RepoRef, val fork: Boolean)

/** GitHub REST API. 로그인하지 않으면 공개 레포만, 시간당 60회까지 조회된다. */
object GitHub {
    // 이전/이름 변경된 레포는 301로 응답하므로 리다이렉트를 따라가 새 full_name을 받는다
    private val http =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    fun byId(id: Long) = repo("repositories/$id")

    fun byRef(ref: RepoRef) = repo("repos/${ref.owner}/${ref.name}")

    private fun repo(path: String): GitHubRepo? {
        val json = get(path) ?: return null
        val (owner, name) = json.string("full_name").split('/', limit = 2)
        return GitHubRepo(json.string("id").toLong(), RepoRef(owner, name), json.string("fork").toBoolean())
    }

    /** 2xx가 아니거나 네트워크 오류면 null. */
    fun get(path: String): JsonObject? = runCatching {
        val request =
            HttpRequest
                .newBuilder(URI.create("https://api.github.com/$path"))
                .header("Accept", "application/vnd.github+json")
                .apply { GitHubAuth.token()?.let { header("Authorization", "Bearer $it") } }
                .timeout(Duration.ofSeconds(20))
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        // 토큰이 폐기됐으면 비로그인으로 돌아간다
        if (response.statusCode() == 401 && GitHubAuth.token() != null) GitHubAuth.logout()
        if (response.statusCode() in 200..299) Json.parseToJsonElement(response.body()).jsonObject else null
    }.getOrNull()

    fun postForm(url: String, form: Map<String, String>): JsonObject {
        val body = form.entries.joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, Charsets.UTF_8)}" }
        val request =
            HttpRequest
                .newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        return Json.parseToJsonElement(http.send(request, HttpResponse.BodyHandlers.ofString()).body()).jsonObject
    }

    fun download(url: String, dest: Path): Boolean {
        val request = HttpRequest.newBuilder(URI.create(url)).build()
        return http.send(request, HttpResponse.BodyHandlers.ofFile(dest)).statusCode() in 200..299
    }
}

fun JsonObject.string(key: String): String = (get(key) as JsonElement?)?.jsonPrimitive?.content.orEmpty()
