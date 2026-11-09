package dev.tomng.alarme.feature.ring.takeover

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Repeatedly broadcasts `ACTION_CLOSE_SYSTEM_DIALOGS` while the ring is up, so a notification shade
 * or long-press-power dialog cannot stay open over the ring screen. Sending that broadcast is
 * restricted for third-party apps on API 31+, where [broadcastAllowed] must be false and the
 * component stays a no-op (the accessibility path takes over there).
 */
class SystemDialogCloser(
    private val context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val broadcastAllowed: Boolean = false,
) {
    private var running = false

    fun start() {
        if (!broadcastAllowed || running) return
        running = true
        handler.post(send)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(send)
    }

    private val send: Runnable = Runnable {
        if (!running) return@Runnable
        sendCloseBroadcast()
        handler.postDelayed(send, INTERVAL_MILLIS)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun sendCloseBroadcast() {
        // Restricted for third-party apps since API 31; only reachable when broadcastAllowed is
        // true, which production never sets on minSdk 35.
        context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
    }

    companion object {
        /** The loop interval: a dialog must not survive longer than ~200 ms. */
        const val INTERVAL_MILLIS = 200L
    }
}
