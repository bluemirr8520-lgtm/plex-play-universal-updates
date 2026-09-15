package io.mirr.plexplay.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mirr.plexplay.R
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic-only regression tests: no server, login, saved preferences, real
 * media, or user's custom-font file is read or modified.
 */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class ExternalSubtitleBridgeDeviceTest {
    @Test(timeout = 45_000L)
    fun srtAndVttEmitPlainCuesWithoutOpeningVideoAndSurvivePausedSeeking() {
        exerciseSidecar(
            mimeType = "application/x-subrip",
            suffix = "srt",
            body = "1\n00:00:00,200 --> 00:00:02,000\n<i>4K 외부 자막 ABC</i>\n\n",
        )
        exerciseSidecar(
            mimeType = "text/vtt",
            suffix = "vtt",
            body = "WEBVTT\n\n00:00.200 --> 00:02.000\n<b>4K 외부 자막 ABC</b>\n\n",
        )
    }

    @Test(timeout = 10_000L)
    fun customVlcFontResolvesTheSuppliedTtfWithoutTouchingSavedUserFont() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val temporaryFont = File.createTempFile("sidecar-font-regression-", ".ttf", context.cacheDir)
        try {
            context.resources.openRawResource(R.font.asia_cinema_b).use { input ->
                temporaryFont.outputStream().use { output -> input.copyTo(output) }
            }
            val enumClass = Class.forName("io.mirr.plexplay.ui.VlcSubtitleFont")
            val custom = enumClass.enumConstants.first { (it as Enum<*>).name == "CUSTOM" }
            val resolver = Class.forName("io.mirr.plexplay.ui.VlcPlayerScreenKt")
                .getDeclaredMethod(
                    "resolveVlcSubtitleTypeface",
                    Context::class.java,
                    enumClass,
                    File::class.java,
                )
                .apply { isAccessible = true }
            val resolved = onMain { resolver.invoke(null, context, custom, temporaryFont) as Typeface }
            val expected = Typeface.createFromFile(temporaryFont)
            val sample = "Plex Play ABCXYZ 가각간 가나다라마"
            fun width(typeface: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = 52f
            }.measureText(sample)

            assertEquals("The custom TTF must reach the VLC overlay font resolver", width(expected), width(resolved), .01f)
            assertTrue(
                "The imported cinema font must not silently fall back to the system face",
                abs(width(resolved) - width(Typeface.DEFAULT)) > .1f,
            )
        } finally {
            assertTrue("The test must remove only its unique temporary font", temporaryFont.delete())
        }
    }

    private fun exerciseSidecar(mimeType: String, suffix: String, body: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val subtitleUri = Uri.parse("https://subtitle-fixture.invalid/only-sidecar.$suffix")
        val opens = CopyOnWriteArrayList<Uri>()
        val bytes = body.toByteArray(Charsets.UTF_8)
        val dataSourceFactory = DataSource.Factory {
            val delegate = ByteArrayDataSource(bytes)
            object : DataSource {
                override fun addTransferListener(transferListener: TransferListener) {
                    delegate.addTransferListener(transferListener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    opens += dataSpec.uri
                    if (dataSpec.uri != subtitleUri) {
                        throw IOException("The subtitle bridge attempted to load non-sidecar media")
                    }
                    return delegate.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    delegate.read(buffer, offset, length)

                override fun getUri(): Uri? = delegate.uri

                override fun close() = delegate.close()
            }
        }
        val text = AtomicReference("")
        val error = AtomicReference<PlaybackException?>(null)
        val bitmapSeen = AtomicBoolean(false)
        var player: ExoPlayer? = null
        try {
            player = onMain {
                val configuration = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setId("plex-sidecar:synthetic-$suffix")
                    .setMimeType(mimeType)
                    .setLanguage("ko")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                val factory = Class.forName("io.mirr.plexplay.ui.UniversalSubtitleBridgeKt")
                    .getDeclaredMethod(
                        "createExternalSubtitleBridgeSource",
                        MediaItem.SubtitleConfiguration::class.java,
                        DataSource.Factory::class.java,
                        java.lang.Long.TYPE,
                    )
                    .apply { isAccessible = true }
                val source = factory.invoke(null, configuration, dataSourceFactory, 12_000_000L) as MediaSource
                ExoPlayer.Builder(context.applicationContext).build().also { bridge ->
                    // Keep ownership even if setup throws before onMain returns.
                    player = bridge
                    bridge.addListener(object : Player.Listener {
                        override fun onCues(cueGroup: CueGroup) {
                            if (cueGroup.cues.any { it.bitmap != null }) bitmapSeen.set(true)
                            // This is the same plain-text boundary consumed by
                            // the application's custom-font subtitle overlay.
                            text.set(cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n\n"))
                        }

                        override fun onPlayerError(playbackException: PlaybackException) {
                            error.set(playbackException)
                        }
                    })
                    bridge.trackSelectionParameters = bridge.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguages("ko", "kor")
                        .setSelectUndeterminedTextLanguage(true)
                        .build()
                    bridge.setMediaSource(source)
                    bridge.playWhenReady = true
                    bridge.prepare()
                }
            }
            val bridge = checkNotNull(player)
            awaitCondition("The $suffix sidecar must produce its own text cue", error) {
                text.get() == "4K 외부 자막 ABC"
            }
            val pausedPosition = onMain {
                bridge.pause()
                assertNull("No video decoder may be active", bridge.videoFormat)
                assertNull("No audio decoder may be active", bridge.audioFormat)
                assertEquals(12_000L, bridge.duration)
                bridge.currentPosition
            }
            Thread.sleep(250L)
            assertTrue(
                "Pausing the subtitle clock must preserve its position",
                abs(onMain { bridge.currentPosition } - pausedPosition) < 100L,
            )
            onMain { bridge.seekTo(8_000L) }
            awaitCondition("Seeking past the final cue must clear text", error) {
                text.get().isEmpty() && onMain {
                    bridge.playbackState == Player.STATE_READY && bridge.currentPosition == 8_000L
                }
            }
            onMain { bridge.seekTo(600L) }
            awaitCondition("Seeking back while paused must restore the styled sidecar as plain text", error) {
                text.get() == "4K 외부 자막 ABC"
            }
            assertFalse(bitmapSeen.get())
            assertTrue("The external subtitle must actually be loaded", opens.isNotEmpty())
            assertTrue("No original 4K video/container URI may be opened", opens.all { it == subtitleUri })
            assertNull(error.get())
        } finally {
            player?.let { bridge -> onMain { bridge.release() } }
        }
    }

    private fun awaitCondition(
        message: String,
        error: AtomicReference<PlaybackException?>,
        predicate: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            error.get()?.let { throw AssertionError("$message (Media3 error ${it.errorCode})") }
            if (predicate()) return
            Thread.sleep(25L)
        }
        throw AssertionError(message)
    }

    private fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<Result<T>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result.set(runCatching(action))
        }
        return result.get().getOrThrow()
    }
}
