package io.mirr.plexplay.data

import io.mirr.plexplay.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class PlexAuthApi(
    private val clientIdentifier: String,
) {
    suspend fun signIn(
        username: String,
        password: String,
    ): String = withContext(Dispatchers.IO) {
        val body = listOf(
            "login" to username.trim(),
            "password" to password,
            "rememberMe" to "true",
        ).joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }.toByteArray(Charsets.UTF_8)
        val url = URI("https://plex.tv/api/v2/users/signin").toURL()
        var lastError: Exception? = null

        for (attempt in 0..1) {
            val http = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
                requestMethod = "POST"
                doOutput = true
                useCaches = false
                setFixedLengthStreamingMode(body.size)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Connection", "close")
                setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8",
                )
                setRequestProperty("X-Plex-Product", "Plex Play Universal")
                setRequestProperty("X-Plex-Version", BuildConfig.VERSION_NAME)
                setRequestProperty("X-Plex-Platform", "Android")
                setRequestProperty("X-Plex-Client-Identifier", clientIdentifier)
            }
            try {
                http.outputStream.use { it.write(body) }
                val status = http.responseCode
                if (status !in 200..299) {
                    val message = when (status) {
                        401 -> "사용자명 또는 비밀번호가 올바르지 않습니다."
                        429 -> "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."
                        else -> "Plex 계정 로그인 오류 ($status)"
                    }
                    throw PlexException(message)
                }
                val json = http.inputStream.bufferedReader().use { it.readText() }
                return@withContext JSONObject(json).optString("authToken")
                    .takeIf { it.isNotBlank() }
                    ?: throw PlexException("Plex 인증 토큰을 받지 못했습니다.")
            } catch (error: PlexException) {
                throw error
            } catch (error: Exception) {
                lastError = error
                if (attempt == 1) break
            } finally {
                http.disconnect()
            }
        }
        throw PlexException(
            "Plex 계정 서버 응답이 중간에 끊겼습니다. 인터넷 연결을 확인해 주세요.",
            lastError,
        )
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
