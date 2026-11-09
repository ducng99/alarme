package dev.tomng.alarme.core.alarmengine.ring

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.FakeAlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery
import dev.tomng.alarme.core.alarmengine.scheduling.RingDeliveryDispatcher
import dev.tomng.alarme.core.alarmengine.scheduling.TriggerType
import dev.tomng.alarme.core.common.time.FakeAppClock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingDeliveryDispatcherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-11-04T06:00:05Z")
    private lateinit var clock: FakeAppClock
    private lateinit var scheduler: FakeAlarmScheduler
    private val started = mutableListOf<Intent>()
    private var startBehaviour: (Intent) -> Unit = {}

    @Before
    fun setUp() {
        clock = FakeAppClock(now, TimeZone.UTC)
        scheduler = FakeAlarmScheduler()
        started.clear()
        startBehaviour = {}
    }

    private fun delivery(isRetry: Boolean = false) =
        RingDelivery(
            alarmId = 2,
            scheduleId = "dev_2_0600_2310",
            alarmTimeMillis = Instant.parse("2026-11-04T06:00:00Z").toEpochMilliseconds(),
            triggerType = TriggerType.NORMAL,
            isRetry = isRetry,
        )

    private fun dispatcher() =
        RingDeliveryDispatcher(
            context = context,
            starter = { intent ->
                started += intent
                startBehaviour(intent)
            },
            scheduler = scheduler,
            clock = clock,
        )

    @Test
    fun `starts the foreground service with the delivery`() {
        dispatcher().dispatch(delivery())

        assertThat(started).hasSize(1)
        assertThat(started.single().component!!.className)
            .isEqualTo("dev.tomng.alarme.core.alarmengine.service.AlarmService")
        assertThat(RingDelivery.fromIntent(started.single())).isEqualTo(delivery())
        assertThat(scheduler.retryArmedAt).isNull()
    }

    @Test
    fun `blocked start retries once three seconds later`() {
        startBehaviour = { throw ForegroundServiceStartNotAllowedException("denied") }

        dispatcher().dispatch(delivery())

        assertThat(scheduler.retryArmedAt).isEqualTo(now + 3.seconds)
        assertThat(scheduler.retryDelivery!!.alarmId).isEqualTo(2)
    }

    @Test
    fun `a retry that is blocked again gives up`() {
        startBehaviour = { throw ForegroundServiceStartNotAllowedException("denied") }
        val thrown = runCatching { dispatcher().dispatch(delivery(isRetry = true)) }

        assertThat(thrown.isFailure).isTrue()
        assertThat(scheduler.retryArmedAt).isNull()
    }

    @Test
    fun `an old delivery gives up instead of retrying`() {
        startBehaviour = { throw ForegroundServiceStartNotAllowedException("denied") }
        val stale =
            delivery()
                .copy(
                    alarmTimeMillis = Instant.parse("2026-11-04T05:00:00Z").toEpochMilliseconds(),
                )
        val thrown = runCatching { dispatcher().dispatch(stale) }

        assertThat(thrown.isFailure).isTrue()
        assertThat(scheduler.retryArmedAt).isNull()
    }
}
