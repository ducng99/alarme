package dev.tomng.alarme.core.alarmengine.ring

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomng.alarme.core.alarmengine.notifications.RingScreenIntentFactory
import javax.inject.Inject
import javax.inject.Singleton

/** Brings the ring screen to the front, whether or not the app currently has a visible activity. */
fun interface RingScreenStarter {
    fun start()
}

/**
 * Sends the ring intent as a [PendingIntent] with the background-activity-start opt-ins on both
 * sides: the creator mode on the intent and the sender mode on the send call. A plain
 * `startActivity` from a foreground service is refused while the app is in the background; the
 * opt-ins are what let the platform honour the launch.
 */
@Singleton
class ContextRingScreenStarter
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val factory: RingScreenIntentFactory,
) : RingScreenStarter {

    override fun start() {
        val sendOptions =
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    backgroundStartMode(),
                )
                .toBundle()
        try {
            ringPendingIntent().send(sendOptions)
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "the ring screen intent was cancelled", e)
        } catch (e: SecurityException) {
            // The platform refuses to promote a background app: the notification's full-screen
            // intent is the remaining path (heads-up while the screen is unlocked and in use).
            Log.w(TAG, "the platform refused to bring the ring screen to the front", e)
        }
    }

    private fun ringPendingIntent(): PendingIntent {
        val creatorOptions =
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                    backgroundStartMode(),
                )
                .toBundle()
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            factory
                .ringIntent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            creatorOptions,
        )
    }

    private fun backgroundStartMode(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }

    private companion object {
        const val TAG = "Alarme.RingScreen"

        const val REQUEST_CODE = 0
    }
}
