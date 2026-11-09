package dev.tomng.alarme.core.alarmengine.scheduling

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.receiver.AlarmPrepareReceiver
import dev.tomng.alarme.core.alarmengine.receiver.AlarmRingReceiver
import dev.tomng.alarme.core.alarmengine.receiver.AlarmWarmupReceiver
import dev.tomng.alarme.core.alarmengine.receiver.WakeUpCheckReceiver
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The intent contract between the scheduler and the receivers (extras round-trip). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingIntentsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `ring intent round trips`() {
        val intent =
            AlarmIntents.ringIntent(
                context = context,
                alarmId = 12L,
                scheduleId = "dev_12_0630_2310",
                alarmTimeMillis = 1_769_000_000_000L,
                triggerType = TriggerType.NORMAL,
            )

        val delivery = RingDelivery.fromIntent(intent)!!

        assertThat(delivery.alarmId).isEqualTo(12L)
        assertThat(delivery.scheduleId).isEqualTo("dev_12_0630_2310")
        assertThat(delivery.alarmTimeMillis).isEqualTo(1_769_000_000_000L)
        assertThat(delivery.triggerType).isEqualTo(TriggerType.NORMAL)
        assertThat(delivery.isRetry).isFalse()
    }

    @Test
    fun `ring intent carries retry flag`() {
        val intent =
            AlarmIntents.ringIntent(
                context,
                alarmId = 1L,
                scheduleId = "dev_1_0600_2300",
                alarmTimeMillis = 1L,
                triggerType = TriggerType.SNOOZE,
                isRetry = true,
            )
        assertThat(RingDelivery.fromIntent(intent)!!.isRetry).isTrue()
    }

    @Test
    fun `malformed intents parse to null`() {
        assertThat(RingDelivery.fromIntent(Intent(AlarmIntents.ACTION_RING))).isNull()

        val missingExtras =
            Intent(context, AlarmRingReceiver::class.java).setAction(AlarmIntents.ACTION_RING)
        assertThat(RingDelivery.fromIntent(missingExtras)).isNull()

        val badScheduleId =
            AlarmIntents.ringIntent(context, 1L, "not a schedule id", 1L, TriggerType.NORMAL)
        assertThat(RingDelivery.fromIntent(badScheduleId)).isNull()
    }

    @Test
    fun `broadcast intents target their receiver and wake stopped packages`() {
        val expectedFlags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND

        val ring = AlarmIntents.ringIntent(context, 1L, "dev_1_0600_2300", 5L, TriggerType.NORMAL)
        assertThat(ring.component!!.className).isEqualTo(AlarmRingReceiver::class.java.name)
        assertThat(ring.flags and expectedFlags).isEqualTo(expectedFlags)

        val warmup = AlarmIntents.warmupIntent(context, acquireWakeLock = true)
        assertThat(warmup.component!!.className).isEqualTo(AlarmWarmupReceiver::class.java.name)
        assertThat(warmup.getBooleanExtra(AlarmIntents.EXTRA_ACQUIRE_WAKE_LOCK, false)).isTrue()

        val prepare = AlarmIntents.prepareIntent(context)
        assertThat(prepare.component!!.className).isEqualTo(AlarmPrepareReceiver::class.java.name)

        val wuc = AlarmIntents.wakeUpCheckIntent(context, alarmId = 9L, alarmTimeMillis = 7L)
        assertThat(wuc.component!!.className).isEqualTo(WakeUpCheckReceiver::class.java.name)
        val parsed = RingDelivery.fromIntent(wuc)!!
        assertThat(parsed.triggerType).isEqualTo(TriggerType.WAKE_UP_CHECK)
        assertThat(parsed.alarmId).isEqualTo(9L)
    }
}
