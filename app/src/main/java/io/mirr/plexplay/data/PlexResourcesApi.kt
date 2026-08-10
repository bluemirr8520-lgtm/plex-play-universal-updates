package io.mirr.plexplay.data

import android.util.Xml
import io.mirr.plexplay.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URI

data class PlexServerConnection(
    val serverName: String,
    val uri: String,
    val token: String,
    val isLocal: Boolean,
    val isRelay: Boolean,
)

class PlexResourcesApi(
    private val accountToken: String,
    private val clientIdentifier: String,
) {
    suspend fun serverConnections(): List<PlexServerConnection> =
        withContext(Dispatchers.IO) {
            val url = URI(
                "https://plex.tv/api/resources?includeHttps=1&includeRelay=1",
            ).toURL()
            val http = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Accept", "application/xml")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Connection", "close")
                setRequestProperty("X-Plex-Token", accountToken)
                setRequestProperty("X-Plex-Product", "Plex Play Universal")
                setRequestProperty("X-Plex-Version", BuildConfig.VERSION_NAME)
                setRequestProperty("X-Plex-Client-Identifier", clientIdentifier)
            }
            try {
                if (http.responseCode !in 200..299) {
                    throw PlexException("Plex 서버 주소를 계정에서 조회하지 못했습니다.")
                }
                parse(http.inputStream)
            } finally {
                http.disconnect()
            }
        }

    private fun parse(input: java.io.InputStream): List<PlexServerConnection> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }
        val result = mutableListOf<PlexServerConnection>()
        var serverName = ""
        var serverToken = ""
        var isServer = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "Device" -> {
                    isServer = parser.attribute("provides")
                        .orEmpty()
                        .split(',')
                        .any { it.trim() == "server" }
                    serverName = parser.attribute("name").orEmpty()
                    serverToken = parser.attribute("accessToken") ?: accountToken
                }
                "Connection" -> if (isServer) {
                    val uri = parser.attribute("uri").orEmpty()
                    if (uri.isNotBlank()) {
                        result += PlexServerConnection(
                            serverName = serverName,
                            uri = uri.trimEnd('/'),
                            token = serverToken,
                            isLocal = parser.attribute("local") == "1",
                            isRelay = parser.attribute("relay") == "1",
                        )
                    }
                }
            }
        }
        return result.sortedWith(
            compareBy<PlexServerConnection> { it.isRelay }
                .thenBy { !it.uri.startsWith("https://") }
                .thenBy { it.isLocal },
        )
    }

    private fun XmlPullParser.attribute(name: String): String? =
        getAttributeValue(null, name)
}
