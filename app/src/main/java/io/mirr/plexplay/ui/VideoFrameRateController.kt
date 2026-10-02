package io.mirr.plexplay.ui

import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Owns only a display hint; never stops, replaces, or releases the decoder's surface. */
internal class VideoFrameRateController(private val view: SurfaceView) : SurfaceHolder.Callback {
    private var desiredRate = 0f
    private var disposed = false
    private var cache = PlaybackViewUpdateCache()

    init { view.holder.addCallback(this) }

    fun update(sourceRate: Double?, speed: Float, playing: Boolean) {
        if (disposed) return
        desiredRate = playbackFrameRate(sourceRate, speed, playing)
        applyHint(view.holder)
    }

    override fun surfaceCreated(holder: SurfaceHolder) { applyHint(holder) }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        applyHint(holder)
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) { cache = PlaybackViewUpdateCache() }

    fun dispose() {
        if (disposed) return
        desiredRate = 0f
        applyHint(view.holder)
        disposed = true
        view.holder.removeCallback(this)
    }

    private fun applyHint(holder: SurfaceHolder) {
        if (disposed || Build.VERSION.SDK_INT < 30) return
        val surface = holder.surface
        if (!surface.isValid) return
        runCatching {
            cache.apply(surface, desiredRate) {
                if (Build.VERSION.SDK_INT >= 31) {
                    surface.setFrameRate(desiredRate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                        Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
                } else {
                    surface.setFrameRate(desiredRate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                }
            }
        }
    }
}
