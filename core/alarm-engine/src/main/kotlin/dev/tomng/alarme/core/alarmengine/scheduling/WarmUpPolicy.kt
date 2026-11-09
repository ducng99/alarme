package dev.tomng.alarme.core.alarmengine.scheduling

/** OEM warm-up rule: how long before the ring the CPU should be pre-woken. */
data class WarmUpOptions(
    /** Lead time in milliseconds; the warm-up alarm fires at `ringAt - leadMillis`. */
    val leadMillis: Long,
    /** Whether the warm-up receiver should take a wakelock. */
    val acquireWakeLock: Boolean,
)

/** The observed OEM warm-up table. */
object WarmUpPolicy {

    fun ruleFor(manufacturer: String, sdkInt: Int): WarmUpOptions? =
        when (manufacturer.lowercase()) {
            "honor" -> WAKE_30_S
            "google" -> WAKE_30_S
            "nothing" -> NO_WAKING_5_S
            "xiaomi" -> if (sdkInt in 28..33) WAKE_30_S else null
            else -> null
        }

    private val WAKE_30_S = WarmUpOptions(leadMillis = 30_000, acquireWakeLock = true)
    private val NO_WAKING_5_S = WarmUpOptions(leadMillis = 5_000, acquireWakeLock = false)
}
