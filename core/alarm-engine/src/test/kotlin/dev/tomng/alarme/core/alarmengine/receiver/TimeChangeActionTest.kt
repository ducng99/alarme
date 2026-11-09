package dev.tomng.alarme.core.alarmengine.receiver

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.FakeAlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.alarmengine.scheduling.TimeChangeGuard
import dev.tomng.alarme.core.common.time.FakeAppClock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimeChangeActionTest {

    @Test
    fun `time set triggers one rearm for the pair`() {
        val clock = FakeAppClock(Instant.parse("2026-11-04T06:00:00Z"), TimeZone.UTC)
        val scheduler = FakeAlarmScheduler()
        val action = timeChangeAction(clock, scheduler)

        action.onTimeChanged(Intent.ACTION_TIME_CHANGED)
        clock.advanceBy(1.seconds)
        action.onTimeChanged(Intent.ACTION_TIMEZONE_CHANGED)

        assertThat(scheduler.reasons).containsExactly(RescheduleReason.TIME_CHANGED)
    }

    @Test
    fun `changes outside the guard window rearm again`() {
        val clock = FakeAppClock(Instant.parse("2026-11-04T06:00:00Z"), TimeZone.UTC)
        val scheduler = FakeAlarmScheduler()
        val action = timeChangeAction(clock, scheduler)

        action.onTimeChanged(Intent.ACTION_TIME_CHANGED)
        clock.advanceBy(TimeChangeGuard.WINDOW + 5.seconds)
        action.onTimeChanged(Intent.ACTION_TIMEZONE_CHANGED)

        assertThat(scheduler.reasons)
            .containsExactly(RescheduleReason.TIME_CHANGED, RescheduleReason.TIME_CHANGED)
    }

    @Test
    fun `unrelated actions are ignored`() {
        val scheduler = FakeAlarmScheduler()
        val action =
            timeChangeAction(
                FakeAppClock(Instant.parse("2026-11-04T06:00:00Z"), TimeZone.UTC),
                scheduler,
            )

        action.onTimeChanged(Intent.ACTION_POWER_CONNECTED)

        assertThat(scheduler.reasons).isEmpty()
    }

    private fun timeChangeAction(
        clock: FakeAppClock,
        scheduler: FakeAlarmScheduler,
    ): TimeChangeAction =
        TimeChangeAction(
            guard = TimeChangeGuard(clock),
            scheduler = scheduler,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
}
