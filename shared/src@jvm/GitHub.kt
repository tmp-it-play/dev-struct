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
import java.util.concurrent.ConcurrentHashMap

data class GitHubRepo(val id: Long, val ref: RepoRef, val fork: Boolean)

/** GitHub REST API. 로그인하지 않으면 공개 저장소만, 시간당 60회까지 조회된다. */
object GitHub {
    // 이전/이름 변경된 저장소는 301로 응답하므로 리다이렉트를 따라가 새 full_name을 받는다
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

    private val etagCache = ConcurrentHashMap<String, Pair<String, String>>()

    // 비공개(비로그인)·삭제된 저장소는 매번 묻지 않는다. 404도 한도를 쓴다
    private val notFoundUntil = ConcurrentHashMap<String, Long>()
    private const val NOT_FOUND_TTL = 6 * 60 * 60 * 1000L

    /** 요청 한도가 초기화되는 시각(epoch ms). 그 전에는 요청을 보내지 않는다 */
    @Volatile var limitedUntil = 0L
        private set

    /** 로그인/로그아웃하면 볼 수 있는 저장소가 달라지므로 기억한 결과를 버린다 */
    fun clearCache() {
        etagCache.clear()
        notFoundUntil.clear()
        limitedUntil = 0
    }

    /**
     * 2xx면 JSON, 404 등이면 null. 요청 한도를 다 쓰면 [GitHubLimitException]을 던진다.
     * 같은 요청은 ETag로 다시 보내 304를 받는다(304는 한도에서 빠지지 않는다).
     */
    fun get(path: String): JsonObject? {
        val now = System.currentTimeMillis()
        if (now < limitedUntil) throw GitHubLimitException(limitedUntil)
        if ((notFoundUntil[path] ?: 0) > now) return null
        val cached = etagCache[path]
        val request =
            HttpRequest
                .newBuilder(URI.create("https://api.github.com/$path"))
                .header("Accept", "application/vnd.github+json")
                .apply { GitHubAuth.token()?.let { header("Authorization", "Bearer $it") } }
                .apply { cached?.let { header("If-None-Match", it.first) } }
                .timeout(Duration.ofSeconds(20))
                .build()
        val response =
            runCatching { http.send(request, HttpResponse.BodyHandlers.ofString()) }.getOrNull() ?: return null
        val status = response.statusCode()
        val header = { name: String -> response.headers().firstValue(name).orElse(null) }
        val resetAt = header("x-ratelimit-reset")?.toLongOrNull()?.times(1000)
        val retryAfter = header("retry-after")?.toLongOrNull()?.let { now + it * 1000 }
        val limited = status == 429 || (status == 403 && (header("x-ratelimit-remaining") == "0" || retryAfter != null))
        if (limited) {
            limitedUntil = retryAfter ?: resetAt ?: (now + 60_000)
            throw GitHubLimitException(limitedUntil)
        }
        val body =
            when {
                status == 304 && cached != null -> cached.second
                status in 200..299 -> response.body().also { b -> header("ETag")?.let { etagCache[path] = it to b } }
                else -> {
                    if (status == 404) notFoundUntil[path] = now + NOT_FOUND_TTL
                    // 토큰이 폐기됐으면 비로그인으로 돌아간다
                    if (status == 401 && GitHubAuth.token() != null) GitHubAuth.logout()
                    return null
                }
            }
        return Json.parseToJsonElement(body).jsonObject
    }

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

class GitHubLimitException(val resetAt: Long) : Exception("GitHub 요청 한도를 다 썼어요")

fun JsonObject.string(key: String): String = (get(key) as JsonElement?)?.jsonPrimitive?.content.orEmpty()
