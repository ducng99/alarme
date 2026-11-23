package dev.tomng.alarme.core.alarmengine.ring

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery
import dev.tomng.alarme.core.alarmengine.scheduling.TriggerType
import dev.tomng.alarme.core.alarmengine.service.AlarmService
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmServiceIntentsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `service intent carries the delivery`() {
        val delivery =
            RingDelivery(
                alarmId = 3,
                scheduleId = "dev_3_0600_2310",
                alarmTimeMillis = 1_000L,
                triggerType = TriggerType.NORMAL,
            )

        val intent = AlarmServiceIntents.serviceIntent(context, delivery)

        assertThat(intent.component!!.className).isEqualTo(AlarmService::class.java.name)
        assertThat(RingDelivery.fromIntent(intent)).isEqualTo(delivery)
        assertThat(
                intent.getStringExtra(AlarmServiceIntents.EXTRA_START_MODE),
            )
            .isEqualTo(AlarmServiceIntents.START_MODE_RECEIVER)
    }

    @Test
    fun `service intent keeps the snooze count of a snooze re-ring`() {
        val delivery =
            RingDelivery(
                alarmId = 3,
                scheduleId = "dev_3_0600_2310",
                alarmTimeMillis = 1_000L,
                triggerType = TriggerType.SNOOZE,
                snoozeCount = 2,
            )

        val intent = AlarmServiceIntents.serviceIntent(context, delivery)

        assertThat(RingDelivery.fromIntent(intent)?.snoozeCount).isEqualTo(2)
    }

    @Test
    fun `manual start mode for previews`() {
        val intent = AlarmServiceIntents.manualStartIntent(context, alarmId = 1)
        assertThat(intent.getStringExtra(AlarmServiceIntents.EXTRA_START_MODE))
            .isEqualTo(AlarmServiceIntents.START_MODE_MANUAL)
    }
}
