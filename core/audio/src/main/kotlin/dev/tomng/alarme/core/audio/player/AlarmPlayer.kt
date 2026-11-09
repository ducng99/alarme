package dev.tomng.alarme.core.audio.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Thin playable-channel seam over ExoPlayer (repeat-one, alarm/media usage attributes, per-channel
 * gain). Implemented by [Media3AlarmPlayer]; faked in unit tests.
 */
interface AlarmPlayer {
    fun prepare(uri: String, usageAlarm: Boolean, repeatOne: Boolean)

    fun play()

    fun pause()

    fun setVolume(gain: Float)

    fun release()
}

fun interface AlarmPlayerFactory {
    fun create(): AlarmPlayer
}

/** Media3-backed player — one instance per logical channel (MAIN, BACKUP_SOUND). */
class Media3AlarmPlayer(private val context: Context) : AlarmPlayer {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    override fun prepare(uri: String, usageAlarm: Boolean, repeatOne: Boolean) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(if (usageAlarm) C.USAGE_ALARM else C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION)
                .build(),
            /* handleAudioFocus = */ false,
        )
        player.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun setVolume(gain: Float) = player.setVolume(gain)

    override fun release() {
        player.stop()
        player.clearMediaItems()
        player.release()
    }
}
