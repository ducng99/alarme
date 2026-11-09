package dev.tomng.alarme.core.alarmengine.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Arming must never let a missing exact-alarm grant break the save that triggered it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArmTargetTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val info = AlarmManager.AlarmClockInfo(1L, null)
    private val operation: PendingIntent =
        PendingIntent.getBroadcast(
            context,
            7,
            Intent("test").setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )

    @Test
    fun `a refused alarm clock reports that nothing was armed`() {
        val target =
            AndroidArmTarget(
                armClock = { _, _ -> throw SecurityException("SCHEDULE_EXACT_ALARM") },
                armExact = { _, _, _ -> true },
                cancelArm = {},
            )

        assertThat(target.setAlarmClock(info, operation)).isFalse()
    }

    @Test
    fun `a refused exact alarm reports that nothing was armed`() {
        val target =
            AndroidArmTarget(
                armClock = { _, _ -> true },
                armExact = { _, _, _ -> throw SecurityException("no grant") },
                cancelArm = {},
            )

        assertThat(target.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 1L, operation))
            .isFalse()
    }

    @Test
    fun `a refused cancel is swallowed`() {
        val target =
            AndroidArmTarget(
                armClock = { _, _ -> true },
                armExact = { _, _, _ -> true },
                cancelArm = { throw SecurityException("no grant") },
            )

        target.cancel(operation)
    }

    @Test
    fun `an arming that reports failure is not counted as armed`() {
        val target =
            AndroidArmTarget(
                armClock = { _, _ -> false },
                armExact = { _, _, _ -> false },
                cancelArm = {},
            )

        assertThat(target.setAlarmClock(info, operation)).isFalse()
        assertThat(target.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 1L, operation))
            .isFalse()
    }

    @Test
    fun `a granted arm reports success`() {
        var clockArmed = false
        val target =
            AndroidArmTarget(
                armClock = { _, _ ->
                    clockArmed = true
                    true
                },
                armExact = { _, _, _ -> true },
                cancelArm = {},
            )

        assertThat(target.setAlarmClock(info, operation)).isTrue()
        assertThat(clockArmed).isTrue()
    }
}
