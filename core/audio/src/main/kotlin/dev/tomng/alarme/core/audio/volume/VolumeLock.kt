package dev.tomng.alarme.core.audio.volume

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * The anti-mute core: while ringing, the stream volume is silently forced
 * (`FLAG_REMOVE_SOUND_AND_VIBRATE` — no beep, no UI toast), re-forced on every `Settings.System`
 * change and every 5 s, and the user's original volume is restored on release.
 *
 * The periodic re-force is a [Handler] runnable, not a coroutine: it must keep running on its own
 * while the ring holds, independent of any dispatcher, and must stop dead on [release].
 *
 * [duck]/[unduck] implement the TTS interplay (halve the stream while announcements play; the
 * periodic re-force then holds the *ducked* target until it is restored). [escalate] is the
 * backup-sound jump to maximum.
 */
class VolumeLock(
    private val context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var stream = AudioManager.STREAM_ALARM
    private var original = 0
    private var lockedTarget = 0
    private var duckedTarget: Int? = null
    private var engaged = false
    private var reforcing = false

    private val reforceRunnable =
        object : Runnable {
            override fun run() {
                if (!reforcing) return
                force()
                handler.postDelayed(this, REFORCE_INTERVAL_MILLIS)
            }
        }

    private val observer =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                force()
            }
        }

    fun engage(stream: Int, target: Int) {
        if (!engaged) {
            engaged = true
            original = audioManager.getStreamVolume(stream)
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                observer,
            )
            reforcing = true
            handler.postDelayed(reforceRunnable, 0)
        }
        this.stream = stream
        lockedTarget = target
        duckedTarget = null
        force()
    }

    /** Temporarily hold a lower target (TTS announcements); periodic re-force keeps it in place. */
    fun duck(target: Int) {
        if (!engaged) return
        duckedTarget = target
        force()
    }

    /** Return to the locked target. */
    fun unduck() {
        if (!engaged) return
        duckedTarget = null
        force()
    }

    /** Backup-sound escalation: raise the locked target itself, keeping the original. */
    fun escalate(target: Int) {
        if (!engaged) return
        duckedTarget = null
        lockedTarget = target
        force()
    }

    fun release() {
        if (!engaged) return
        engaged = false
        reforcing = false
        handler.removeCallbacks(reforceRunnable)
        context.contentResolver.unregisterContentObserver(observer)
        // Only write on deviation: a redundant setStreamVolume re-notifies Settings.System, which
        // would re-trigger the observer and ping-pong forever.
        if (audioManager.getStreamVolume(stream) != original) {
            audioManager.setStreamVolume(
                stream,
                original,
                AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE,
            )
        }
    }

    private fun force() {
        if (!engaged) return
        val desired = duckedTarget ?: lockedTarget
        if (audioManager.getStreamVolume(stream) == desired) return
        audioManager.setStreamVolume(stream, desired, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
    }

    companion object {
        const val REFORCE_INTERVAL_MILLIS = 5_000L
    }
}
