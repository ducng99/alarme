package dev.tomng.alarme.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import dev.tomng.alarme.core.designsystem.R
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.delay

/** The wall clock, re-read on every minute boundary so relative times stay current. */
@Composable
fun rememberMinuteTicker(): Instant {
    val now by
        produceState(currentInstant()) {
            while (true) {
                val millis = System.currentTimeMillis()
                delay(MINUTE_MILLIS - millis % MINUTE_MILLIS)
                value = currentInstant()
            }
        }
    return now
}

/** "7 h 12 min": how long until [to], rounded up to the minute so it never reads 0 too early. */
@Composable fun durationText(from: Instant, to: Instant): String = durationText(to - from)

@Composable
fun durationText(duration: Duration): String {
    if (duration < MINUTE) return stringResource(R.string.ds_duration_under_minute)
    val totalMinutes = (duration.inWholeSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    val days = (totalMinutes / MINUTES_PER_DAY).toInt()
    val hours = (totalMinutes % MINUTES_PER_DAY / MINUTES_PER_HOUR).toInt()
    val minutes = (totalMinutes % MINUTES_PER_HOUR).toInt()
    return when {
        days > 0 && hours > 0 -> stringResource(R.string.ds_duration_days_hours, days, hours)
        days > 0 -> stringResource(R.string.ds_duration_days, days)
        hours > 0 && minutes > 0 ->
            stringResource(R.string.ds_duration_hours_minutes, hours, minutes)
        hours > 0 -> stringResource(R.string.ds_duration_hours, hours)
        else -> stringResource(R.string.ds_duration_minutes, minutes)
    }
}

private fun currentInstant(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())

private const val MINUTE_MILLIS = 60_000L
private val MINUTE = 60_000.milliseconds
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 1_440L
