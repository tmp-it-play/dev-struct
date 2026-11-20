package team.themoment.dev_struct

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

// GitHub OAuth App(Device Flow 활성화)의 Client ID. 공개 값이라 코드에 둬도 된다
const val GITHUB_CLIENT_ID = "Ov23liKPaJuNeYyE3Ovf"

data class DeviceCode(
    val userCode: String,
    val verificationUri: String,
    val deviceCode: String,
    val intervalSeconds: Int,
)

/** Device Flow로 로그인하고 토큰은 macOS 키체인에 둔다. */
object GitHubAuth {
    private const val SERVICE = "team.themoment.devstruct"
    private const val ACCOUNT = "github"

    @Volatile private var cached: String? = null

    val configured get() = GITHUB_CLIENT_ID.isNotBlank()

    fun token(): String? = cached ?: runCommand("security", "find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w")
        .takeIf { it.ok && it.output.isNotBlank() }
        ?.output
        ?.also { cached = it }

    fun requestCode(): DeviceCode? {
        // 비공개 레포 메타데이터를 읽으려면 OAuth App은 repo 스코프가 필요하다
        val json = runCatching {
            GitHub.postForm(
                "https://github.com/login/device/code",
                mapOf("client_id" to GITHUB_CLIENT_ID, "scope" to "repo"),
            )
        }.getOrNull()
        if (json == null || json.string("device_code").isEmpty()) return null
        return DeviceCode(
            json.string("user_code"),
            json.string("verification_uri"),
            json.string("device_code"),
            json.string("interval").toIntOrNull() ?: 5,
        )
    }

    /** 사용자가 브라우저에서 승인할 때까지 기다린다. 실패하면 이유를 돌려준다. */
    suspend fun awaitToken(code: DeviceCode): String? {
        var interval = code.intervalSeconds
        while (true) {
            delay(interval.seconds)
            val json =
                runCatching {
                    GitHub.postForm(
                        "https://github.com/login/oauth/access_token",
                        mapOf(
                            "client_id" to GITHUB_CLIENT_ID,
                            "device_code" to code.deviceCode,
                            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        ),
                    )
                }.getOrNull() ?: continue
            val token = json.string("access_token")
            if (token.isNotEmpty()) {
                save(token)
                return null
            }
            when (val error = json.string("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                else -> return "로그인 실패: $error"
            }
        }
    }

    private fun save(token: String) {
        runCommand("security", "add-generic-password", "-U", "-s", SERVICE, "-a", ACCOUNT, "-w", token)
        cached = token
        GitHub.clearCache()
    }

    fun logout() {
        runCommand("security", "delete-generic-password", "-s", SERVICE, "-a", ACCOUNT)
        cached = null
        GitHub.clearCache()
    }
}
