package dev.tomng.alarme.core.alarmengine.scheduling

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The OEM warm-up table. */
class WarmUpPolicyTest {

    @Test
    fun `honor wakes 30 seconds early with wakelock`() {
        val rule = WarmUpPolicy.ruleFor(manufacturer = "honor", sdkInt = 35)
        assertThat(rule).isEqualTo(WarmUpOptions(leadMillis = 30_000, acquireWakeLock = true))
    }

    @Test
    fun `google wakes 30 seconds early with wakelock`() {
        val rule = WarmUpPolicy.ruleFor(manufacturer = "Google", sdkInt = 36)
        assertThat(rule).isEqualTo(WarmUpOptions(leadMillis = 30_000, acquireWakeLock = true))
    }

    @Test
    fun `nothing wakes 5 seconds early without wakelock`() {
        val rule = WarmUpPolicy.ruleFor(manufacturer = "nothing", sdkInt = 35)
        assertThat(rule).isEqualTo(WarmUpOptions(leadMillis = 5_000, acquireWakeLock = false))
    }

    @Test
    fun `xiaomi only on sdk 28 to 33`() {
        assertThat(WarmUpPolicy.ruleFor("xiaomi", 31))
            .isEqualTo(WarmUpOptions(leadMillis = 30_000, acquireWakeLock = true))
        assertThat(WarmUpPolicy.ruleFor("xiaomi", 28)).isNotNull()
        assertThat(WarmUpPolicy.ruleFor("xiaomi", 33)).isNotNull()
        assertThat(WarmUpPolicy.ruleFor("xiaomi", 34)).isNull()
    }

    @Test
    fun `unknown manufacturers get no warm-up`() {
        assertThat(WarmUpPolicy.ruleFor("samsung", 35)).isNull()
        assertThat(WarmUpPolicy.ruleFor("OnePlus", 35)).isNull()
    }
}
