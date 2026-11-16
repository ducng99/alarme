package dev.tomng.alarme.feature.editor

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tomng.alarme.core.designsystem.component.NumberWheel
import dev.tomng.alarme.core.designsystem.component.alarmContainerKey
import dev.tomng.alarme.core.designsystem.component.durationText
import dev.tomng.alarme.core.designsystem.component.rememberMinuteTicker
import dev.tomng.alarme.core.designsystem.component.sharedContainer
import dev.tomng.alarme.core.designsystem.theme.Spacing
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlinx.coroutines.flow.drop
import kotlinx.datetime.DayOfWeek

/**
 * The time, set in place: snapping hour/minute (and AM/PM) wheels with a live "rings in" line. The
 * keyboard button swaps the wheels for typed entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeHero(
    alarmId: Long,
    hour: Int,
    minute: Int,
    nextRing: Instant?,
    onTime: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth().sharedContainer(alarmContainerKey(alarmId)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.l).testTag("editor_time"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = typing,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "timeMode",
                ) { keyboard ->
                    if (keyboard) {
                        TypedTime(hour, minute, is24Hour, onTime)
                    } else {
                        TimeWheels(hour, minute, is24Hour, onTime)
                    }
                }
                IconButton(
                    onClick = { typing = !typing },
                    modifier =
                        Modifier.align(Alignment.TopEnd)
                            .padding(end = Spacing.s)
                            .testTag("editor_time_mode"),
                ) {
                    Icon(
                        imageVector =
                            if (typing) Icons.Outlined.Schedule else Icons.Outlined.Keyboard,
                        contentDescription = stringResource(R.string.editor_time_keyboard),
                    )
                }
            }
            RingsIn(nextRing)
        }
    }
}

@Composable
private fun TimeWheels(hour: Int, minute: Int, is24Hour: Boolean, onTime: (Int, Int) -> Unit) {
    val style = MaterialTheme.typography.displayLarge
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val pm = hour >= NOON
        if (is24Hour) {
            NumberWheel(
                values = (0 until HOURS_PER_DAY).toList(),
                selected = hour,
                onSelect = { onTime(it, minute) },
                label = { "%02d".format(it) },
                contentDescription = stringResource(R.string.editor_hour),
                textStyle = style,
                width = WHEEL_WIDTH,
                testTag = "editor_hour",
            )
        } else {
            NumberWheel(
                values = (1..NOON).toList(),
                selected = (hour % NOON).let { if (it == 0) NOON else it },
                onSelect = { h12 -> onTime(h12 % NOON + if (pm) NOON else 0, minute) },
                label = { it.toString() },
                contentDescription = stringResource(R.string.editor_hour),
                textStyle = style,
                width = WHEEL_WIDTH,
                testTag = "editor_hour",
            )
        }
        Text(text = ":", style = style)
        NumberWheel(
            values = (0 until MINUTES_PER_HOUR).toList(),
            selected = minute,
            onSelect = { onTime(hour, it) },
            label = { "%02d".format(it) },
            contentDescription = stringResource(R.string.editor_minute),
            textStyle = style,
            width = WHEEL_WIDTH,
            testTag = "editor_minute",
        )
        if (!is24Hour) {
            MeridiemToggle(
                pm = pm,
                onPm = { wantPm -> onTime(hour % NOON + if (wantPm) NOON else 0, minute) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MeridiemToggle(pm: Boolean, onPm: (Boolean) -> Unit) {
    val description = stringResource(R.string.editor_meridiem)
    Column(
        modifier =
            Modifier.padding(start = Spacing.s).semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        listOf(false, true).forEachIndexed { index, option ->
            ToggleButton(
                checked = pm == option,
                onCheckedChange = { onPm(option) },
                shapes =
                    if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                    else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                contentPadding = PaddingValues(horizontal = Spacing.m),
                modifier =
                    Modifier.height(Spacing.xxxl).testTag(if (option) "editor_pm" else "editor_am"),
            ) {
                Text(meridiemLabel(option), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypedTime(hour: Int, minute: Int, is24Hour: Boolean, onTime: (Int, Int) -> Unit) {
    val state =
        rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = is24Hour)
    LaunchedEffect(state) {
        snapshotFlow { state.hour to state.minute }.drop(1).collect { (h, m) -> onTime(h, m) }
    }
    TimeInput(
        state = state,
        modifier = Modifier.padding(top = Spacing.xxl).testTag("editor_time_input"),
    )
}

@Composable
private fun RingsIn(nextRing: Instant?) {
    val now = rememberMinuteTicker()
    val text =
        if (nextRing == null) {
            stringResource(R.string.editor_rings_off)
        } else {
            stringResource(R.string.editor_rings_in, durationText(now, nextRing)) +
                " · " +
                dayAndTime(nextRing)
        }
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ringsIn",
    ) { shown ->
        Text(
            text = shown,
            style = MaterialTheme.typography.titleSmall,
            modifier =
                Modifier.padding(top = Spacing.xs, start = Spacing.l, end = Spacing.l)
                    .testTag("editor_rings_in"),
        )
    }
}

private fun dayAndTime(at: Instant): String {
    val zoned =
        java.time.Instant.ofEpochMilli(at.toEpochMilliseconds())
            .atZone(java.time.ZoneId.systemDefault())
    val day = zoned.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
    return "$day " +
        DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).format(zoned)
}

private fun meridiemLabel(pm: Boolean): String =
    LocalTime.of(if (pm) NOON + 1 else 1, 0)
        .format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))

/** Seven round toggles for the repeat days, and the common presets beneath them. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DayPicker(
    days: Set<DayOfWeek>,
    onToggleDay: (DayOfWeek) -> Unit,
    onDays: (Set<DayOfWeek>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            orderedWeek().forEach { day ->
                val label = dayName(day, java.time.format.TextStyle.FULL)
                ToggleButton(
                    checked = day in days,
                    onCheckedChange = { onToggleDay(day) },
                    shapes = ToggleButtonDefaults.shapesFor(DAY_SIZE),
                    contentPadding = PaddingValues(0.dp),
                    modifier =
                        Modifier.size(DAY_SIZE)
                            .semantics { contentDescription = label }
                            .testTag("editor_day:${day.name}"),
                ) {
                    Text(
                        text = dayName(day, java.time.format.TextStyle.SHORT).take(2),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            val presets =
                listOf(
                    Triple(emptySet<DayOfWeek>(), R.string.editor_once, "editor_once"),
                    Triple(WEEKDAYS, R.string.editor_weekdays, "editor_weekdays"),
                    Triple(WEEKENDS, R.string.editor_weekends, "editor_weekends"),
                    Triple(
                        DayOfWeek.entries.toSet(),
                        R.string.editor_every_day,
                        "editor_every_day",
                    ),
                )
            presets.forEachIndexed { index, (set, label, tag) ->
                FilledTonalToggleButton(
                    checked = days == set,
                    onCheckedChange = { onDays(set) },
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            presets.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    contentPadding = PaddingValues(horizontal = Spacing.xs),
                    modifier = Modifier.weight(1f).testTag(tag),
                ) {
                    BasicText(
                        text = stringResource(label),
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                color = LocalContentColor.current,
                            ),
                        maxLines = 1,
                        autoSize =
                            TextAutoSize.StepBased(
                                minFontSize = PRESET_MIN_FONT,
                                maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                            ),
                    )
                }
            }
        }
    }
}

private val PRESET_MIN_FONT = 9.sp

/** The week in the locale's order: Monday first in most places, Sunday first in others. */
private fun orderedWeek(): List<DayOfWeek> {
    val first = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek.value - 1
    return List(DayOfWeek.entries.size) { DayOfWeek.entries[(first + it) % DayOfWeek.entries.size] }
}

private fun dayName(day: DayOfWeek, style: java.time.format.TextStyle): String =
    java.time.DayOfWeek.of(day.ordinal + 1).getDisplayName(style, Locale.getDefault())

private val WEEKDAYS =
    setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )
private val WEEKENDS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private val WHEEL_WIDTH = 96.dp
private val DAY_SIZE = 48.dp
private const val NOON = 12
private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
