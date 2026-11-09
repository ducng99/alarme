package dev.tomng.alarme.feature.ringtones

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Short in-picker audition; faked in tests. */
fun interface PreviewPlayer {
    fun play(uri: String)

    fun stop() {}
}

/** Single ExoPlayer instance re-prepared per preview, media usage (not the alarm stream). */
class ExoPreviewPlayer(context: Context) : PreviewPlayer {

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
        }

    override fun play(uri: String) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    fun release() = player.release()
}
