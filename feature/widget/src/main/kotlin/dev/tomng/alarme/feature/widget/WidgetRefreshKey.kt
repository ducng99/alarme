package dev.tomng.alarme.feature.widget

import dev.tomng.alarme.core.model.alarm.Alarm
import kotlin.time.Instant

/**
 * The alarm fields the widget renders. The app repaints the widget when this changes, so renaming
 * the alarm or moving its time updates the widget even while the next occurrence stays the same.
 */
data class WidgetRefreshKey(
    val id: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val nextOccurrence: Instant?,
) {
    companion object {
        fun of(alarm: Alarm?): WidgetRefreshKey? = alarm?.let {
            WidgetRefreshKey(
                id = it.id,
                name = it.name,
                hour = it.hour,
                minute = it.minute,
                nextOccurrence = it.nextOccurrence,
            )
        }
    }
}
