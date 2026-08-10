package io.mirr.plexplay.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ConnectionStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences("plex_connection", Context.MODE_PRIVATE)

    fun load(): PlexConnection = PlexConnection(
        baseUrl = preferences.getString("base_url", "").orEmpty(),
        token = if (preferences.getInt("auth_version", 0) >= 3) loadToken() else "",
    )

    fun save(connection: PlexConnection) {
        preferences.edit()
            .putString("base_url", normalizeUrl(connection.baseUrl))
            .putString("token_encrypted", encrypt(connection.token.trim()))
            .putInt("auth_version", 3)
            .remove("token")
            .apply()
    }

    fun logout() {
        preferences.edit()
            .remove("base_url")
            .remove("token_encrypted")
            .remove("token")
            .remove("auth_version")
            .apply()
    }

    fun clientIdentifier(): String {
        val existing = preferences.getString("client_identifier", null)
        if (existing != null) return existing
        return UUID.randomUUID().toString().also {
            preferences.edit().putString("client_identifier", it).apply()
        }
    }

    fun libraryOrder(): List<String> =
        preferences.getString("library_order", "")
            .orEmpty()
            .split(',')
            .filter { it.isNotBlank() }

    fun saveLibraryOrder(keys: List<String>) {
        preferences.edit()
            .putString("library_order", keys.joinToString(","))
            .apply()
    }

    fun playbackQuality(): PlaybackQuality {
        if (!preferences.getBoolean("direct_play_default_v1", false)) {
            preferences.edit()
                .putString(
                    "playback_quality",
                    PlaybackQuality.ORIGINAL.name,
                )
                .putBoolean("direct_play_default_v1", true)
                .apply()
            return PlaybackQuality.ORIGINAL
        }
        return runCatching {
            PlaybackQuality.valueOf(
                preferences.getString("playback_quality", null)
                    ?: PlaybackQuality.ORIGINAL.name,
            )
        }.getOrDefault(PlaybackQuality.ORIGINAL)
    }

    fun savePlaybackQuality(quality: PlaybackQuality) {
        preferences.edit()
            .putString("playback_quality", quality.name)
            .apply()
    }

    fun autoPlayNext(): Boolean =
        preferences.getBoolean("auto_play_next", false)

    fun saveAutoPlayNext(enabled: Boolean) {
        preferences.edit()
            .putBoolean("auto_play_next", enabled)
            .apply()
    }

    private fun normalizeUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private fun loadToken(): String {
        val encrypted = preferences.getString("token_encrypted", null)
        if (encrypted != null) return decrypt(encrypted)
        return preferences.getString("token", "").orEmpty()
    }

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val payload = Base64.decode(value, Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val encrypted = payload.copyOfRange(IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "plex_play_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
