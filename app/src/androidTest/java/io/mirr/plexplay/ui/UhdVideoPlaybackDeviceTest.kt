package io.mirr.plexplay.ui

import android.graphics.PixelFormat
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
 * Synthetic-only software-decoder smoke tests for the bundled LibVLC 3.7.5.
 * Each input is 3840x2160, six SMPTE color-bar frames at 3 fps, two seconds,
 * generated locally with FFmpeg 7.0, with no downloaded or real-user media.
 *
 * The decoded input is scaled to a private 640x360 ImageReader surface, which
 * is drained and closed without showing an Activity or touching settings.
 * This proves neither real-time 4K playback nor hardware decoding, 4K display,
 * HDR/Dolby Vision presentation, DRM support, or every profile of these codecs.
 *
 * Common generation arguments:
 * -f lavfi -i smptebars=size=3840x2160:rate=3:duration=2 -an -map_metadata -1
 * H264: -c:v libx264 -threads 2 -preset ultrafast -crf 28 -pix_fmt yuv420p
 * HEVC: -c:v libx265 -threads 2 -preset ultrafast
 *       -x265-params pools=2:frame-threads=1:log-level=error -crf 30 -pix_fmt yuv420p
 * AV1:  -c:v libaom-av1 -threads 2 -cpu-used 8 -row-mt 1 -crf 38 -b:v 0 -pix_fmt yuv420p10le
 * VP9:  -c:v libvpx-vp9 -threads 2 -deadline realtime -cpu-used 8 -crf 38 -b:v 0 -pix_fmt yuv420p10le
 * All use Matroska. The installed HEVC encoder is 8-bit-only; there is NO
 * HEVC Main10 fixture or Main10 coverage claim here.
 */
@RunWith(AndroidJUnit4::class)
class UhdVideoPlaybackDeviceTest {
    @Test(timeout = 45_000L)
    fun bundledLibVlcSoftwareDecodesUhdH264EightBit() = exerciseVideo(
        "uhd_smoke_h264_8bit.mkv",
        "cdff71ec2a30b3827323db5f3dea854f83ac3bccb77be889b291d1ab4b1d31b2",
        "h264",
    )

    @Test(timeout = 45_000L)
    fun bundledLibVlcSoftwareDecodesUhdHevcEightBit() = exerciseVideo(
        "uhd_smoke_hevc_8bit.mkv",
        "c9a39813c86deb5cdfa0b5f87ebbda9e9417cfd0d276883861c5260a9b0642b9",
        "hevc",
    )

    @Test(timeout = 45_000L)
    fun bundledLibVlcSoftwareDecodesUhdAv1TenBit() = exerciseVideo(
        "uhd_smoke_av1_10bit.mkv",
        "7597365fc8d2cc79837fedd7007a15a51460f480f0a8d2044cbec7ef91eefe63",
        "av01",
    )

    @Test(timeout = 45_000L)
    fun bundledLibVlcSoftwareDecodesUhdVp9TenBit() = exerciseVideo(
        "uhd_smoke_vp9_10bit.mkv",
        "1eba0a641d520a2e1375c2da13f21b7a55b6e3edd39acc44f2c974977fda9edf",
        "VP90",
    )

    private fun exerciseVideo(assetName: String, expectedSha256: String, expectedFourcc: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File.createTempFile("uhd-decoder-smoke-", ".mkv", context.cacheDir)
        var libVlc: LibVLC? = null
        var media: Media? = null
        var player: MediaPlayer? = null
        var imageReader: ImageReader? = null
        val playing = AtomicBoolean(false)
        val ended = AtomicBoolean(false)
        val error = AtomicBoolean(false)
        val surfaceError = AtomicReference<Throwable?>(null)
        val offscreenFrames = AtomicInteger(0)
        val progressedMs = AtomicLong(0L)
        var decodedVideo = 0
        var displayedPictures = 0
        var selectedVideo = false

        try {
            instrumentation.context.assets.open(assetName).use { input ->
                fixture.outputStream().use { output -> input.copyTo(output) }
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(fixture.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertEquals("Use the verified synthetic codec/bit-depth fixture", expectedSha256, digest)

            val engine = onMain {
                LibVLC(
                    context.applicationContext,
                    arrayListOf(
                        "--no-audio", "--no-spu", "--stats", "--avcodec-hw=none",
                        "--no-drop-late-frames", "--no-skip-frames",
                    ),
                ).also { libVlc = it }
            }
            val source = onMain {
                Media(engine, Uri.fromFile(fixture)).also {
                    media = it
                    // Deliberately video-only: do NOT copy this restricted list into
                    // production, where Opus/FLAC/SPU may need other native modules.
                    it.addOption(":codec=dav1d,avcodec,none")
                    it.addOption(":file-caching=100")
                }
            }
            assertTrue("Parse the local UHD fixture", source.parse(IMedia.Parse.ParseLocal))
            assertEquals("The fixture contains one video stream and no audio", 1, source.trackCount)
            val track = source.getTrack(0)
            assertTrue("The fixture stream must be video", track is IMedia.VideoTrack)
            val videoTrack = track as IMedia.VideoTrack
            assertEquals("The coded input must be 3840 pixels wide", 3840, videoTrack.width)
            assertEquals("The coded input must be 2160 pixels high", 2160, videoTrack.height)
            assertEquals("LibVLC must identify the expected codec", fourcc(expectedFourcc), videoTrack.fourcc)

            val output = onMain {
                ImageReader.newInstance(640, 360, PixelFormat.RGBA_8888, 2).also { reader ->
                    imageReader = reader
                    reader.setOnImageAvailableListener(
                        { available ->
                            try {
                                // Drain every notification so the private producer cannot stall.
                                available.acquireLatestImage()?.use { offscreenFrames.incrementAndGet() }
                            } catch (failure: Throwable) {
                                surfaceError.compareAndSet(null, failure)
                            }
                        },
                        Handler(Looper.getMainLooper()),
                    )
                }
            }
            val decoder = onMain {
                MediaPlayer(engine).also { active ->
                    player = active
                    active.setEventListener { event ->
                        when (event.type) {
                            MediaPlayer.Event.Playing -> playing.set(true)
                            MediaPlayer.Event.EndReached -> ended.set(true)
                            MediaPlayer.Event.EncounteredError -> error.set(true)
                            MediaPlayer.Event.TimeChanged ->
                                progressedMs.updateAndGet { maxOf(it, event.timeChanged) }
                        }
                    }
                    active.getVLCVout().apply {
                        setVideoSurface(output.surface, null)
                        setWindowSize(640, 360)
                        // No layout listener: the documented API selects GLES output,
                        // which can render to the valid ImageReader Surface offscreen.
                        attachViews()
                    }
                    active.media = source
                    active.play()
                }
            }

            val deadline = SystemClock.uptimeMillis() + 20_000L
            while (SystemClock.uptimeMillis() < deadline) {
                onMain {
                    source.stats?.let { stats ->
                        decodedVideo = maxOf(decodedVideo, stats.decodedVideo)
                        displayedPictures = maxOf(displayedPictures, stats.displayedPictures)
                    }
                    selectedVideo = selectedVideo || decoder.videoTrack >= 0
                    progressedMs.updateAndGet { maxOf(it, decoder.time) }
                }
                if (error.get() || surfaceError.get() != null || ended.get()) break
                Thread.sleep(25L)
            }

            val details = "$assetName: decoded=$decodedVideo, rendered=$displayedPictures, " +
                "offscreen=" + offscreenFrames.get() + ", time=" + progressedMs.get() + " ms"
            surfaceError.get()?.let { throw AssertionError("Offscreen output failed ($details)", it) }
            assertFalse("LibVLC must not report a playback error ($details)", error.get())
            assertTrue("LibVLC must enter Playing ($details)", playing.get())
            assertTrue("LibVLC must select the UHD video stream ($details)", selectedVideo)
            assertTrue("The media clock must advance ($details)", progressedMs.get() >= 500L)
            assertTrue("Decode all six synthetic UHD frames in software ($details)", decodedVideo >= 6)
            assertTrue("The video output must render pictures ($details)", displayedPictures > 0)
            assertTrue("The private offscreen surface must receive frames ($details)", offscreenFrames.get() > 0)
            assertTrue("The complete synthetic clip must reach its end ($details)", ended.get())
        } finally {
            try {
                player?.let { active ->
                    onMain {
                        active.setEventListener(null)
                        try {
                            active.stop()
                        } finally {
                            try {
                                active.getVLCVout().detachViews()
                            } finally {
                                active.release()
                            }
                        }
                    }
                }
            } finally {
                try {
                    imageReader?.let { reader ->
                        onMain {
                            reader.setOnImageAvailableListener(null, null)
                            reader.close()
                        }
                    }
                } finally {
                    try {
                        media?.let { source -> onMain { source.release() } }
                    } finally {
                        try {
                            libVlc?.let { engine -> onMain { engine.release() } }
                        } finally {
                            assertTrue("Remove only the test's unique temporary fixture", fixture.delete())
                        }
                    }
                }
            }
        }
    }

    private fun fourcc(value: String): Int {
        require(value.length == 4)
        return value.indices.fold(0) { result, index -> result or (value[index].code shl (index * 8)) }
    }

    private fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<Result<T>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result.set(runCatching(action))
        }
        return result.get().getOrThrow()
    }
}

