package dev.tomng.alarme.core.alarmengine.ring

import android.content.Intent

/** Actions into the ring UI (`feature:ring` declares the matching intent-filter). */
object RingIntents {
    /** Opens the full-screen ring takeover. */
    const val ACTION_OPEN_RING = "dev.tomng.alarme.action.OPEN_RING"

    fun openRingIntent(packageName: String): Intent =
        Intent(ACTION_OPEN_RING).setPackage(packageName)
}
