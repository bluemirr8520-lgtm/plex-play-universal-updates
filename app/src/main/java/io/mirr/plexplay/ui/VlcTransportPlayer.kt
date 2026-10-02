package io.mirr.plexplay.ui

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** UI-only adapter: no second decoder, video surface, audio output, or subtitle renderer. */
internal class VlcTransportPlayer(
    private val onPlayWhenReady: (Boolean) -> Unit,
    private val onSeek: (Long) -> Unit,
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private var snapshot = TransportSnapshot()
    private val position = PositionSupplier { snapshot.positionMs }

    fun update(value: TransportSnapshot) {
        val changed = value.copy(positionMs = snapshot.positionMs) != snapshot
        snapshot = value
        // PlayerControlView polls the supplier. Clock ticks must not rebuild its timeline.
        if (changed) invalidateState()
    }

    override fun getState(): State {
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE, Player.COMMAND_RELEASE,
        ).apply {
            if (snapshot.durationMs > 0) addAll(
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
            )
            if (snapshot.hasPrevious) add(Player.COMMAND_SEEK_TO_PREVIOUS)
            if (snapshot.hasNext) add(Player.COMMAND_SEEK_TO_NEXT)
        }.build()
        val item = MediaItemData.Builder("vlc-transport")
            .setMediaItem(MediaItem.EMPTY)
            .setIsSeekable(snapshot.durationMs > 0)
            .setDurationUs(snapshot.durationMs.takeIf { it > 0 }?.times(1_000) ?: C.TIME_UNSET)
            .build()
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            .setPlayWhenReady(snapshot.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (snapshot.buffering) Player.STATE_BUFFERING else Player.STATE_READY)
            .setIsLoading(snapshot.buffering)
            .setContentPositionMs(position)
            .setContentBufferedPositionMs(position)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        snapshot = snapshot.copy(playing = playWhenReady)
        onPlayWhenReady(playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                if (snapshot.hasPrevious) onPrevious()
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                if (snapshot.hasNext) onNext()
            else -> {
                val target = transportSeekPosition(positionMs, snapshot.durationMs)
                snapshot = snapshot.copy(positionMs = target)
                onSeek(target)
            }
        }
        return Futures.immediateVoidFuture()
    }
}
