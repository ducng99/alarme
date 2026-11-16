package dev.tomng.alarme.core.audio.host

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomng.alarme.core.alarmengine.ring.RingHost
import dev.tomng.alarme.core.alarmengine.ring.RingRequest
import dev.tomng.alarme.core.audio.di.RingAudioScope
import dev.tomng.alarme.core.audio.player.AlarmPlayer
import dev.tomng.alarme.core.audio.player.AlarmPlayerFactory
import dev.tomng.alarme.core.audio.tts.TtsSpeaker
import dev.tomng.alarme.core.audio.tts.TtsUtterances
import dev.tomng.alarme.core.audio.vibration.RingVibrator
import dev.tomng.alarme.core.audio.volume.VolumeLock
import dev.tomng.alarme.core.audio.volume.crescendoFloor
import dev.tomng.alarme.core.audio.volume.crescendoGain
import dev.tomng.alarme.core.audio.volume.targetVolume
import dev.tomng.alarme.core.common.time.AppClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

/**
 * The ring audio engine, wired into the ring service through the engine's [RingHost] seam:
 *
 * * stream = ALARM when the sound is on, MUSIC when deliberately routed externally;
 * * the volume lock engages at `ceil(max * ratio)` and restores on stop;
 * * audio focus `GAIN_TRANSIENT` + `USAGE_ALARM` (not for previews);
 * * crescendo: quadratic player-gain ramp from a ratio-dependent floor;
 * * TTS: first round at +3 s — pauses the main sound, ducks the stream to <= max/2, resumes after
 *   the round, repeats on the interval — but only when no backup sound is configured;
 * * backup: at +delay the main sound stops, the stream escalates to MAX, and the loud backup
 *   ringtone plays on its own repeat-one channel.
 */
@Singleton
class RingAudioHost
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @RingAudioScope private val scope: CoroutineScope,
    private val clock: AppClock,
    private val players: AlarmPlayerFactory,
    private val speaker: TtsSpeaker,
) : RingHost {

    private val audioManager: AudioManager? by lazy {
        context.getSystemService(AudioManager::class.java)
    }
    private val vibrator by lazy { RingVibrator(context) }

    private var ringJob: Job? = null
    private var volumeLock: VolumeLock? = null
    private var main: AlarmPlayer? = null
    private var backup: AlarmPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    @Volatile private var backupStarted = false

    override fun start(request: RingRequest) {
        stop()
        if (request.volumeRatio <= 0f) return // "silent" alarm: no sound, no vibration, no lock
        val manager = audioManager ?: return
        val stream =
            if (request.useAlarmStream) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
        val max = manager.getStreamMaxVolume(stream)
        val target = targetVolume(max, request.volumeRatio)

        volumeLock = VolumeLock(context).also { it.engage(stream, target) }
        if (!request.isPreview) requestFocus(manager)
        if (request.vibrate) vibrator.start()

        val main = players.create()
        request.audioUri?.let {
            main.prepare(it, usageAlarm = request.useAlarmStream, repeatOne = true)
        }
        main.play()
        this.main = main

        ringJob = scope.launch {
            val crescendoJob =
                request.crescendo?.let { launchCrescendo(main, it, request.volumeRatio) }
            val ttsJob =
                request.tts
                    ?.takeIf { !request.isPreview }
                    ?.let {
                        launchTts(
                            it,
                            main,
                            target.coerceAtMost(max / 2),
                            repeat = request.backupSound == null,
                        )
                    }
            request.backupSound?.let { backupOption ->
                delay(backupOption.delaySeconds * 1_000L)
                crescendoJob?.cancel()
                ttsJob?.cancel()
                speaker.stop()
                backupStarted = true
                main.pause()
                volumeLock?.escalate(max) // escalate to maximum for the backup sound
                backup =
                    players.create().also {
                        it.prepare(backupOption.backupUri, usageAlarm = true, repeatOne = true)
                        it.play()
                    }
            }
            awaitCancellation() // held open until stop()
        }
    }

    override fun stop() {
        ringJob?.cancel()
        ringJob = null
        speaker.stop()
        main?.release()
        main = null
        backup?.release()
        backup = null
        backupStarted = false
        volumeLock?.release()
        volumeLock = null
        vibrator.stop()
        abandonFocus()
    }

    private fun CoroutineScope.launchCrescendo(
        main: AlarmPlayer,
        option: RingRequest.Crescendo,
        ratio: Float,
    ) = launch {
        if (option.durationSeconds <= 0) {
            main.setVolume(1f)
            return@launch
        }
        val floor = crescendoFloor(ratio)
        var second = 0
        while (isActive && second <= option.durationSeconds) {
            main.setVolume(crescendoGain(second, option.durationSeconds, floor))
            delay(1_000)
            second++
        }
    }

    private fun CoroutineScope.launchTts(
        option: RingRequest.Tts,
        main: AlarmPlayer,
        duckTarget: Int,
        repeat: Boolean,
    ) = launch {
        delay(FIRST_ANNOUNCEMENT_DELAY_MILLIS)
        while (isActive) {
            val utterances =
                TtsUtterances.build(
                    label = option.label,
                    speakLabel = option.speakLabel,
                    speakTime = option.speakTime,
                    time = clock.now().toLocalDateTime(clock.zone()).time,
                )
            if (utterances.isNotEmpty()) {
                main.pause()
                volumeLock?.duck(duckTarget)
                try {
                    speaker.speak(utterances)
                } finally {
                    if (!backupStarted) {
                        main.play()
                        volumeLock?.unduck()
                    }
                }
            }
            if (!repeat) break
            delay(option.repeatIntervalMillis)
        }
    }

    private fun requestFocus(manager: AudioManager) {
        val request =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
        manager.requestAudioFocus(request)
        focusRequest = request
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    companion object {
        /** The first announcement is scheduled 3 s after the ring starts. */
        const val FIRST_ANNOUNCEMENT_DELAY_MILLIS = 3_000L
    }
}
