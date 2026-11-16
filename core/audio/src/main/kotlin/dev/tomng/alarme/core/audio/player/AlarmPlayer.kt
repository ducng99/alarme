package dev.tomng.alarme.core.audio.player

import android.content.Context
import android.media.RingtoneManager
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.tomng.alarme.core.audio.R

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

/**
 * Media3-backed player - one instance per logical channel (MAIN, BACKUP_SOUND). A sound that fails
 * to open moves on to the next candidate so a ring is never silent.
 */
class Media3AlarmPlayer(private val context: Context) : AlarmPlayer {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var candidates: List<String> = emptyList()
    private var candidateIndex = 0

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "Cannot play ${candidates.getOrNull(candidateIndex)}", error)
                    playNextCandidate()
                }
            },
        )
    }

    override fun prepare(uri: String, usageAlarm: Boolean, repeatOne: Boolean) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(if (usageAlarm) C.USAGE_ALARM else C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION)
                .build(),
            /* handleAudioFocus = */ false,
        )
        player.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        candidates =
            alarmSoundCandidates(
                requested = uri,
                systemDefault = systemDefaultAlarmUri(),
                bundledFallback = bundledFallbackUri(),
            )
        candidateIndex = 0
        player.setMediaItem(MediaItem.fromUri(candidates[candidateIndex]))
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

    private fun playNextCandidate() {
        if (candidateIndex >= candidates.lastIndex) return
        candidateIndex++
        player.setMediaItem(MediaItem.fromUri(candidates[candidateIndex]))
        player.prepare()
    }

    private fun systemDefaultAlarmUri(): String? = runCatching {
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
            )
    }
        .getOrNull()
        ?.toString()

    private fun bundledFallbackUri(): String =
        "android.resource://${context.packageName}/${R.raw.alarme_radar}"

    private companion object {
        const val TAG = "AlarmPlayer"
    }
}
