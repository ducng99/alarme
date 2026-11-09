package dev.tomng.alarme.core.alarmengine.scheduling

import com.google.common.truth.Truth.assertThat
import kotlin.time.Instant
import org.junit.Test

/** Early-wake prepare window. */
class PreparePolicyTest {

    private val now = Instant.parse("2026-11-03T04:00:00Z")

    private fun prepareAt(
        hour: Int,
        skipped: Boolean = false,
        trigger: TriggerType = TriggerType.NORMAL,
        hasWarmUpRule: Boolean = false,
    ): Instant? =
        PreparePolicy.prepareAt(
            hour = hour,
            minute = 0,
            skipped = skipped,
            triggerType = trigger,
            occurrence = Instant.parse("2026-11-03T${hour.toString().padStart(2, '0')}:00:00Z"),
            hasWarmUpRule = hasWarmUpRule,
            now = now,
        )

    @Test
    fun `alarms between 0500 and 1059 are prepared`() {
        assertThat(prepareAt(5)).isNotNull()
        assertThat(prepareAt(10)).isNotNull()
    }

    @Test
    fun `alarms outside the window get no prepare`() {
        assertThat(prepareAt(4)).isNull() // 04:00 — before 05:00
        assertThat(prepareAt(11)).isNull() // 11:00 — window ends at 10:59
    }

    @Test
    fun `prepare fires 5 minutes before without OEM warm-up`() {
        val at = prepareAt(6, hasWarmUpRule = false)!!
        assertThat(at).isEqualTo(Instant.parse("2026-11-03T05:55:00Z"))
    }

    @Test
    fun `prepare fires 15 minutes before on OEM warm-up devices`() {
        val at = prepareAt(6, hasWarmUpRule = true)!!
        assertThat(at).isEqualTo(Instant.parse("2026-11-03T05:45:00Z"))
    }

    @Test
    fun `skipped alarms get no prepare`() {
        assertThat(prepareAt(6, skipped = true)).isNull()
    }

    @Test
    fun `snooze triggers get no prepare`() {
        assertThat(prepareAt(6, trigger = TriggerType.SNOOZE)).isNull()
    }

    @Test
    fun `wake-up check triggers get no prepare`() {
        assertThat(prepareAt(6, trigger = TriggerType.WAKE_UP_CHECK)).isNull()
    }

    @Test
    fun `prepare time never lands in the past`() {
        // Now 05:58, alarm 06:00, lead 5 min -> raw 05:55 is past, clamp to now.
        val at =
            PreparePolicy.prepareAt(
                hour = 6,
                minute = 0,
                skipped = false,
                triggerType = TriggerType.NORMAL,
                occurrence = Instant.parse("2026-11-03T06:00:00Z"),
                hasWarmUpRule = false,
                now = Instant.parse("2026-11-03T05:58:00Z"),
            )!!
        assertThat(at).isEqualTo(Instant.parse("2026-11-03T05:58:00Z"))
    }
}
