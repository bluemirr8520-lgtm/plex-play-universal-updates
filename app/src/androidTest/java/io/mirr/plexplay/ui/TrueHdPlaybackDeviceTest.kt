package io.mirr.plexplay.ui

import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

/**
 * Tests the bundled LibVLC native decoder with synthesized 2-second tone fixtures.
 * No server, account, real media, saved preferences, system volume, or audio focus
 * is read or changed. Only this private MediaPlayer is muted.
 *
 * Positive decode/output-buffer counters with a forced PCM device establish
 * software TrueHD playback, not audible fidelity, HDMI passthrough, or Atmos.
 *
 * Fixture generation (FFmpeg 7.0, no downloaded input):
 * ffmpeg -f lavfi -i sine=frequency=440:sample_rate=48000:duration=2
 *   -af "pan=stereo|c0=c0|c1=c0" -c:a truehd -strict -2 -map_metadata -1
 *   -f matroska truehd_smoke_stereo_48000.mka
 *
 * The 5.1(side) fixture uses aevalsrc with amplitude 0.05 and distinct frequencies
 * FL=440, FR=550, FC=660, LFE=60, SL=770, SR=880 Hz, s=48000:d=2:c=5.1(side).
 * It uses the same TrueHD options plus -sample_fmt s16p. This exercises six-channel
 * input decoding to the device's PCM route; it does not assert six output speakers.
 */
@RunWith(AndroidJUnit4::class)
class TrueHdPlaybackDeviceTest {
    @Test(timeout = 30_000L)
    fun bundledLibVlcDecodesSyntheticTrueHdToPcm() {
        exerciseTrueHd(
            assetName = "truehd_smoke_stereo_48000.mka",
            expectedSha256 = "7f0787c3855144256c1b313dbb531d4e073b7d42baaaf5d28e05539428a1dc51",
            expectedChannels = 2,
        )
    }

    @Test(timeout = 30_000L)
    fun bundledLibVlcDecodesSyntheticFivePointOneTrueHdToPcm() {
        exerciseTrueHd(
            assetName = "truehd_smoke_5_1_48000.mka",
            expectedSha256 = "2b82708dc25cc00c00a038ecf6b2fd73640a3c9ee686c3fcb7d951cc205678c7",
            expectedChannels = 6,
        )
    }

    private fun exerciseTrueHd(assetName: String, expectedSha256: String, expectedChannels: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File.createTempFile("truehd-decoder-smoke-", ".mka", context.cacheDir)
        var libVlc: LibVLC? = null
        var media: Media? = null
        var player: MediaPlayer? = null
        val playing = AtomicBoolean(false)
        val ended = AtomicBoolean(false)
        val error = AtomicBoolean(false)
        val progressedMs = AtomicLong(0L)
        var decodedAudio = 0
        var playedAudioBuffers = 0
        var selectedAudio = false

        try {
            instrumentation.context.assets.open(assetName).use { input ->
                fixture.outputStream().use { output -> input.copyTo(output) }
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(fixture.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertEquals(
                "The smoke test must play the verified synthetic TrueHD fixture",
                expectedSha256,
                digest,
            )

            val engine = onMain {
                LibVLC(context.applicationContext, arrayListOf("--aout=android_audiotrack", "--no-video", "--stats"))
                    .also { libVlc = it }
            }
            val source = onMain { Media(engine, Uri.fromFile(fixture)).also { media = it } }
            // Parse only a local file; neither network fetch nor user interaction is allowed.
            assertTrue("LibVLC must parse the synthetic TrueHD container", source.parse(IMedia.Parse.ParseLocal))
            assertEquals("The fixture contains exactly one audio stream", 1, source.trackCount)
            val track = source.getTrack(0)
            assertTrue("The fixture stream must be audio", track is IMedia.AudioTrack)
            val audioTrack = track as IMedia.AudioTrack
            assertEquals(expectedChannels, audioTrack.channels)
            assertEquals(48_000, audioTrack.rate)
            val trueHdFourcc = 't'.code or ('r'.code shl 8) or ('h'.code shl 16) or ('d'.code shl 24)
            assertEquals("LibVLC must identify the input as TrueHD, not a PCM fallback fixture", trueHdFourcc, audioTrack.fourcc)

            val decoder = onMain {
                MediaPlayer(engine).also { active ->
                    // Retain ownership even if any setup assertion throws.
                    player = active
                    assertTrue(active.setAudioOutput("android_audiotrack"))
                    assertTrue(active.setAudioDigitalOutputEnabled(false))
                    // Pin the PCM output explicitly, so plug detection cannot choose encoded output.
                    assertTrue(active.setAudioOutputDevice("pcm"))
                    active.setVolume(0)
                    active.setEventListener { event ->
                        when (event.type) {
                            MediaPlayer.Event.Playing -> playing.set(true)
                            MediaPlayer.Event.EndReached -> ended.set(true)
                            MediaPlayer.Event.EncounteredError -> error.set(true)
                            MediaPlayer.Event.TimeChanged ->
                                progressedMs.updateAndGet { maxOf(it, event.timeChanged) }
                        }
                    }
                    active.media = source
                    active.play()
                }
            }

            val deadline = SystemClock.uptimeMillis() + 12_000L
            while (SystemClock.uptimeMillis() < deadline) {
                onMain {
                    source.stats?.let { stats ->
                        decodedAudio = maxOf(decodedAudio, stats.decodedAudio)
                        playedAudioBuffers = maxOf(playedAudioBuffers, stats.playedAbuffers)
                    }
                    selectedAudio = selectedAudio || decoder.audioTrack >= 0
                    progressedMs.updateAndGet { maxOf(it, decoder.time) }
                }
                if (error.get() || ended.get()) break
                Thread.sleep(25L)
            }

            val details = "$assetName: decoded=$decodedAudio, output=$playedAudioBuffers, time=${progressedMs.get()} ms"
            assertFalse("LibVLC reported a playback error ($details)", error.get())
            assertTrue("LibVLC must enter Playing ($details)", playing.get())
            assertTrue("LibVLC must select the TrueHD audio stream ($details)", selectedAudio)
            assertTrue("The media clock must advance ($details)", progressedMs.get() >= 500L)
            assertTrue("The bundled native decoder must decode audio frames ($details)", decodedAudio > 0)
            assertTrue("The forced PCM output must play audio buffers ($details)", playedAudioBuffers > 0)
            assertTrue("The complete synthetic clip must reach its end ($details)", ended.get())
        } finally {
            try {
                player?.let { active ->
                    onMain {
                        active.setEventListener(null)
                        try {
                            active.stop()
                        } finally {
                            active.release()
                        }
                    }
                }
            } finally {
                try {
                    media?.let { source -> onMain { source.release() } }
                } finally {
                    try {
                        libVlc?.let { engine -> onMain { engine.release() } }
                    } finally {
                        assertTrue("Remove only the test's unique temporary media file", fixture.delete())
                    }
                }
            }
        }
    }

    private fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<Result<T>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result.set(runCatching(action))
        }
        return result.get().getOrThrow()
    }
}
