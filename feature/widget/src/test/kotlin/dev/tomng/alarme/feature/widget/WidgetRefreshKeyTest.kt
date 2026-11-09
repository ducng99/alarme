package dev.tomng.alarme.feature.widget

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.alarm.Alarm
import kotlin.time.Instant
import org.junit.Test

class WidgetRefreshKeyTest {

    private val now = Instant.parse("2026-11-10T06:00:00Z")

    private fun alarm(
        id: Long = 1,
        name: String = "Work",
        hour: Int = 7,
        minute: Int = 30,
        nextOccurrence: Instant? = Instant.parse("2026-11-10T07:30:00Z"),
    ) =
        Alarm(
            id = id,
            name = name,
            hour = hour,
            minute = minute,
            createdAt = now,
            updatedAt = now,
            nextOccurrence = nextOccurrence,
        )

    @Test
    fun `no alarm has no key`() {
        assertThat(WidgetRefreshKey.of(null)).isNull()
    }

    @Test
    fun `an unchanged alarm keeps the same key`() {
        assertThat(WidgetRefreshKey.of(alarm())).isEqualTo(WidgetRefreshKey.of(alarm()))
    }

    @Test
    fun `renaming the alarm changes the key`() {
        assertThat(WidgetRefreshKey.of(alarm(name = "Workout")))
            .isNotEqualTo(WidgetRefreshKey.of(alarm(name = "Work")))
    }

    @Test
    fun `moving the alarm changes the key`() {
        assertThat(WidgetRefreshKey.of(alarm(hour = 8, minute = 0)))
            .isNotEqualTo(WidgetRefreshKey.of(alarm(hour = 7, minute = 30)))
    }

    @Test
    fun `a new occurrence changes the key`() {
        assertThat(
                WidgetRefreshKey.of(alarm(nextOccurrence = Instant.parse("2026-11-11T07:30:00Z"))),
            )
            .isNotEqualTo(WidgetRefreshKey.of(alarm()))
    }

    @Test
    fun `a different alarm changes the key`() {
        assertThat(WidgetRefreshKey.of(alarm(id = 2)))
            .isNotEqualTo(WidgetRefreshKey.of(alarm(id = 1)))
    }
}
