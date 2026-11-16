package dev.tomng.alarme.feature.ringtones

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Short in-picker audition; faked in tests. */
interface PreviewPlayer {
    /** The uri being auditioned, or null when silent. */
    val playing: StateFlow<String?>

    fun play(uri: String)

    fun stop()
}

/** Single ExoPlayer instance re-prepared per preview, media usage (not the alarm stream). */
class ExoPreviewPlayer(context: Context) : PreviewPlayer {

    override val playing = MutableStateFlow<String?>(null)

    private val player: ExoPlayer =
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) playing.value = null
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        playing.value = null
                    }
                },
            )
        }

    override fun play(uri: String) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
        playing.value = uri
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
        playing.value = null
    }

    fun release() = player.release()
}
