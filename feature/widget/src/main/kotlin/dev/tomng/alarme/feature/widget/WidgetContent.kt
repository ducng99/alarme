package dev.tomng.alarme.feature.widget

import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.scheduling.NextOccurrenceCalculator
import java.time.Instant as JavaInstant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaZoneId

/**
 * What the widget shows. Pure data so the mapping can be tested without a launcher: the countdown
 * itself is a `Chronometer` in the widget's RemoteViews, which ticks without further updates.
 */
data class WidgetContent(
    val hasAlarm: Boolean,
    val timeText: String,
    val label: String,
    /** Countdown base in elapsed-realtime millis, or null when nothing is armed. */
    val countdownBaseMillis: Long?,
)

/** Maps the next alarm to the widget's text. */
object WidgetContentMapper {

    fun map(
        alarm: Alarm?,
        now: Instant,
        zone: TimeZone,
        elapsedRealtimeMillis: Long,
        locale: Locale = Locale.getDefault(),
    ): WidgetContent {
        if (alarm == null) {
            return WidgetContent(
                hasAlarm = false,
                timeText = "",
                label = "",
                countdownBaseMillis = null,
            )
        }
        val occurrence =
            alarm.nextOccurrence ?: NextOccurrenceCalculator.nextOccurrence(alarm, now, zone)
        return WidgetContent(
            hasAlarm = true,
            timeText = formatTime(alarm, zone, locale),
            label = alarm.name,
            countdownBaseMillis =
                occurrence?.let {
                    elapsedRealtimeMillis + (it.toEpochMilliseconds() - now.toEpochMilliseconds())
                },
        )
    }

    private fun formatTime(alarm: Alarm, zone: TimeZone, locale: Locale): String =
        JavaInstant.ofEpochMilli(0)
            .atZone(zone.toJavaZoneId())
            .withHour(alarm.hour)
            .withMinute(alarm.minute)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}
