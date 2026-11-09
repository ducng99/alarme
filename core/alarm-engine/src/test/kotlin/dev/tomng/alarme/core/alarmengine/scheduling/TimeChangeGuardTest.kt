package dev.tomng.alarme.core.alarmengine.scheduling

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.common.time.FakeAppClock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import org.junit.Test

/** TIME_SET / TIMEZONE_CHANGED arrive as a pair — only the first must trigger a re-arm. */
class TimeChangeGuardTest {

    @Test
    fun `first event acts`() {
        val guard =
            TimeChangeGuard(FakeAppClock(Instant.parse("2026-11-03T06:00:00Z"), TimeZone.UTC))
        assertThat(guard.shouldAct()).isTrue()
    }

    @Test
    fun `second event within the window is swallowed`() {
        val clock = FakeAppClock(Instant.parse("2026-11-03T06:00:00Z"), TimeZone.UTC)
        val guard = TimeChangeGuard(clock)
        guard.shouldAct()
        clock.advanceBy(2.seconds) // pair arrives ~instantly
        assertThat(guard.shouldAct()).isFalse()
    }

    @Test
    fun `event after the window acts again`() {
        val clock = FakeAppClock(Instant.parse("2026-11-03T06:00:00Z"), TimeZone.UTC)
        val guard = TimeChangeGuard(clock)
        guard.shouldAct()
        clock.advanceBy(TimeChangeGuard.WINDOW + 1.seconds)
        assertThat(guard.shouldAct()).isTrue()
    }
}
