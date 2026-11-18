package dev.tomng.alarme.feature.widget

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import java.util.Locale
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import org.junit.Test

class WidgetContentMapperTest {

    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-11-10T06:00:00Z")
    private val elapsedRealtime = 5_000_000L

    private fun alarm(
        hour: Int = 7,
        minute: Int = 30,
        name: String = "Work",
        days: DaysOfWeek = DaysOfWeek.EVERY_DAY,
    ) =
        Alarm(
            id = 1,
            name = name,
            hour = hour,
            minute = minute,
            daysOfWeek = days,
            createdAt = now,
            updatedAt = now,
        )

    private fun map(
        alarm: Alarm?,
        elapsedRealtimeMillis: Long = elapsedRealtime,
        locale: Locale = Locale.US,
    ) = WidgetContentMapper.map(alarm, now, zone, elapsedRealtimeMillis, locale)

    @Test
    fun `no alarm shows the empty state`() {
        val content = map(null)

        assertThat(content.hasAlarm).isFalse()
        assertThat(content.countdownBaseMillis).isNull()
    }

    @Test
    fun `an alarm shows its time and label`() {
        val content = map(alarm(hour = 7, minute = 30))

        assertThat(content.hasAlarm).isTrue()
        assertThat(content.timeText).isEqualTo("7:30 AM")
        assertThat(content.label).isEqualTo("Work")
    }

    @Test
    fun `the countdown targets the next occurrence`() {
        val content = map(alarm(hour = 7, minute = 30))

        // 07:30 UTC today is 90 minutes after the fixed "now".
        assertThat(content.countdownBaseMillis).isEqualTo(elapsedRealtime + 90 * 60 * 1000L)
    }

    @Test
    fun `a one-shot alarm in the past counts down to tomorrow`() {
        val content = map(alarm(hour = 5, minute = 0, days = DaysOfWeek.NONE))

        assertThat(content.countdownBaseMillis).isEqualTo(elapsedRealtime + 23 * 60 * 60 * 1000L)
    }

    @Test
    fun `a repeating alarm skips days it does not repeat on`() {
        // 2026-11-10 is a Tuesday; an alarm on Mondays only fires on the 16th.
        val content = map(alarm(hour = 7, minute = 0, days = DaysOfWeek.of(DayOfWeek.MONDAY)))

        assertThat(content.countdownBaseMillis)
            .isEqualTo(elapsedRealtime + 6 * 24 * 60 * 60 * 1000L + 60 * 60 * 1000L)
    }

    @Test
    fun `the countdown base is relative to the elapsed realtime anchor`() {
        val alarm = alarm(hour = 7, minute = 30)

        val first = map(alarm, elapsedRealtimeMillis = 1_000L)
        val second = map(alarm, elapsedRealtimeMillis = 601_000L)

        assertThat(first.countdownBaseMillis).isEqualTo(1_000L + 90 * 60 * 1000L)
        assertThat(second.countdownBaseMillis).isEqualTo(first.countdownBaseMillis!! + 600_000L)
    }

    @Test
    fun `the time follows the locale's clock convention`() {
        val content = map(alarm(hour = 19, minute = 5), locale = Locale.US)
        val german = map(alarm(hour = 19, minute = 5), locale = Locale.GERMANY)

        assertThat(content.timeText).isEqualTo("7:05 PM")
        assertThat(german.timeText).isEqualTo("19:05")
    }
}
