package io.mirr.plexplay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

internal object AppUpdater {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/bluemirr8520-lgtm/plex-play-universal-updates/releases/latest"
    private const val APK_ASSET = "PlexPlayUniversal.apk"
    private const val SHA_ASSET = "PlexPlayUniversal.apk.sha256"
    private const val PREFS = "app_updater"
    private const val PENDING_APK = "pending_apk"

    private data class Release(
        val version: String,
        val apkUrl: String,
        val checksumUrl: String,
    )

    suspend fun checkForUpdates(activity: Activity) {
        try {
            val release = withContext(Dispatchers.IO) { fetchLatestRelease() } ?: return
            if (!isNewer(release.version, BuildConfig.VERSION_NAME)) return

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    activity,
                    "새 버전 ${release.version} 다운로드 중",
                    Toast.LENGTH_SHORT,
                ).show()
            }

            val apk = withContext(Dispatchers.IO) {
                downloadAndVerify(activity, release)
            }
            if (apk == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        activity,
                        "업데이트 파일 검증에 실패했습니다.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                return
            }

            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PENDING_APK, apk.absolutePath)
                .apply()
            withContext(Dispatchers.Main) {
                installPendingUpdate(activity)
            }
        } catch (_: Exception) {
            // 네트워크가 없거나 업데이트 서버가 응답하지 않아도 앱은 정상 실행합니다.
        }
    }

    fun installPendingUpdate(activity: Activity) {
        val preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val path = preferences.getString(PENDING_APK, null) ?: return
        val apk = File(path)
        if (!apk.isFile) {
            preferences.edit().remove(PENDING_APK).apply()
            return
        }

        if (!activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                activity,
                "Plex Play의 '알 수 없는 앱 설치'를 허용해 주세요.",
                Toast.LENGTH_LONG,
            ).show()
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            return
        }

        val apkUri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk,
        )
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        preferences.edit().remove(PENDING_APK).apply()
        activity.startActivity(installIntent)
    }

    private fun fetchLatestRelease(): Release? {
        val connection = openConnection(LATEST_RELEASE_API)
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val version = json.optString("tag_name").removePrefix("v").removePrefix("V")
            if (version.isBlank()) return null

            var apkUrl: String? = null
            var checksumUrl: String? = null
            val assets = json.optJSONArray("assets") ?: return null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                when (asset.optString("name")) {
                    APK_ASSET -> apkUrl = asset.optString("browser_download_url")
                    SHA_ASSET -> checksumUrl = asset.optString("browser_download_url")
                }
            }
            if (apkUrl.isNullOrBlank() || checksumUrl.isNullOrBlank()) null
            else Release(version, apkUrl, checksumUrl)
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadAndVerify(context: Context, release: Release): File? {
        val checksumText = downloadText(release.checksumUrl) ?: return null
        val expectedHash = checksumText.trim().split(Regex("\\s+")).firstOrNull()
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            ?: return null

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(updatesDir, "$APK_ASSET.part")
        val destination = File(updatesDir, APK_ASSET)
        partial.delete()

        val connection = openConnection(release.apkUrl)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }

        if (!sha256(partial).equals(expectedHash, ignoreCase = true)) {
            partial.delete()
            return null
        }
        destination.delete()
        return if (partial.renameTo(destination)) destination else null
    }

    private fun downloadText(url: String): String? {
        val connection = openConnection(url)
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PlexPlay/${BuildConfig.VERSION_NAME}")
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = versionParts(candidate)
        val currentParts = versionParts(current)
        val size = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until size) {
            val candidatePart = candidateParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (candidatePart != currentPart) return candidatePart > currentPart
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        version.removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
}
