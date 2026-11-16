package dev.tomng.alarme.feature.alarmlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.AlarmeSwitch
import dev.tomng.alarme.core.designsystem.component.ClockTime
import dev.tomng.alarme.core.designsystem.component.alarmContainerKey
import dev.tomng.alarme.core.designsystem.component.durationText
import dev.tomng.alarme.core.designsystem.component.rememberFeedback
import dev.tomng.alarme.core.designsystem.component.sharedContainer
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AlarmType
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import java.util.Locale
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/**
 * An alarm card with its gestures: swipe towards the start to delete, towards the end to skip (or
 * un-skip) the next ring, long-press for the same actions in a menu. Every gesture is also an
 * accessibility action so it works without touch.
 */
@Composable
internal fun SwipeableAlarmCard(
    alarm: Alarm,
    now: Instant,
    selected: Boolean,
    callbacks: AlarmListCallbacks,
    modifier: Modifier = Modifier,
) {
    val feedback = rememberFeedback()
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState()
    val canSkip = alarm.enabled && alarm.daysOfWeek.isRepeating

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) feedback.threshold()
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = canSkip,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> callbacks.onDelete(alarm)
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (alarm.isSkipped) callbacks.onUndoSkip(alarm) else callbacks.onSkip(alarm)
                    scope.launch { dismissState.reset() }
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart ->
                    SwipeBackground(
                        icon = Icons.Outlined.Delete,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                        alignment = Alignment.CenterEnd,
                        armed = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart,
                    )
                SwipeToDismissBoxValue.StartToEnd ->
                    SwipeBackground(
                        icon =
                            if (alarm.isSkipped) Icons.AutoMirrored.Outlined.Undo
                            else Icons.Outlined.EventBusy,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                        alignment = Alignment.CenterStart,
                        armed = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd,
                    )
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        AlarmCard(
            alarm = alarm,
            now = now,
            selected = selected,
            canSkip = canSkip,
            callbacks = callbacks,
        )
    }
}

@Composable
private fun SwipeBackground(
    icon: ImageVector,
    container: Color,
    content: Color,
    alignment: Alignment,
    armed: Boolean,
) {
    val scale by
        animateFloatAsState(
            if (armed) ARMED_SCALE else 1f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "swipeIcon",
        )
    Box(
        modifier =
            Modifier.fillMaxSize()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(container)
                .padding(horizontal = Spacing.xl),
        contentAlignment = alignment,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(
    alarm: Alarm,
    now: Instant,
    selected: Boolean,
    canSkip: Boolean,
    callbacks: AlarmListCallbacks,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val feedback = rememberFeedback()
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by
        animateColorAsState(
            when {
                selected -> colors.secondaryContainer
                alarm.enabled -> colors.surfaceContainerHigh
                else -> colors.surfaceContainerLow
            },
            animationSpec = motion.defaultEffectsSpec(),
            label = "cardContainer",
        )
    val timeColor by
        animateColorAsState(
            if (alarm.enabled) colors.onSurface
            else colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
            animationSpec = motion.defaultEffectsSpec(),
            label = "timeColor",
        )

    val skipLabel =
        stringResource(
            if (alarm.isSkipped) R.string.alarmlist_undo_skip else R.string.alarmlist_skip_once,
        )
    val soundLabel = stringResource(R.string.alarmlist_sound)
    val deleteLabel = stringResource(R.string.alarmlist_delete)
    val skip = { if (alarm.isSkipped) callbacks.onUndoSkip(alarm) else callbacks.onSkip(alarm) }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        modifier = Modifier.fillMaxWidth().sharedContainer(alarmContainerKey(alarm.id)),
    ) {
        Box {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClickLabel = stringResource(R.string.alarmlist_edit),
                            onClick = { callbacks.onOpenEditor(alarm.id) },
                            onLongClick = {
                                feedback.longPress()
                                menuOpen = true
                            },
                        )
                        .semantics {
                            customActions = buildList {
                                if (canSkip || alarm.isSkipped) {
                                    add(
                                        CustomAccessibilityAction(skipLabel) {
                                            skip()
                                            true
                                        },
                                    )
                                }
                                add(
                                    CustomAccessibilityAction(soundLabel) {
                                        callbacks.onPickSound(alarm)
                                        true
                                    },
                                )
                                add(
                                    CustomAccessibilityAction(deleteLabel) {
                                        callbacks.onDelete(alarm)
                                        true
                                    },
                                )
                            }
                        }
                        .padding(
                            start = Spacing.xl,
                            end = Spacing.l,
                            top = Spacing.l,
                            bottom = Spacing.l,
                        )
                        .testTag("alarmlist:card:${alarm.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    ClockTime(
                        hour = alarm.hour,
                        minute = alarm.minute,
                        style = MaterialTheme.typography.displaySmall,
                        color = timeColor,
                    )
                    LabelLine(alarm)
                    ScheduleLine(alarm, now)
                    AlarmBadges(alarm)
                }
                AlarmeSwitch(
                    checked = alarm.enabled,
                    onCheckedChange = { callbacks.onToggle(alarm, it) },
                    modifier = Modifier.testTag("alarmlist:toggle:${alarm.id}"),
                )
            }
            AlarmActionsMenu(
                alarm = alarm,
                canSkip = canSkip,
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                callbacks = callbacks,
            )
        }
    }
}

@Composable
private fun LabelLine(alarm: Alarm) {
    val label = alarm.name.ifBlank { null } ?: return
    Text(
        text = "${alarm.emoji}  $label",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The repeat days as a row of letters (active ones highlighted), then when it rings next. */
@Composable
private fun ScheduleLine(alarm: Alarm, now: Instant) {
    val days = alarm.daysOfWeek
    val description = scheduleText(alarm, now)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        if (days.isRepeating && days != DaysOfWeek.EVERY_DAY) {
            DayStrip(days, alarm.enabled)
        } else {
            Text(
                text = repeatText(alarm, now),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val at = alarm.nextOccurrence
        if (at != null && !alarm.isSkipped) {
            Text(
                text = "·  " + stringResource(R.string.alarmlist_in, durationText(now, at)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayStrip(days: DaysOfWeek, enabled: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        DayOfWeek.entries.forEach { day ->
            val active = day in days
            Box(
                modifier =
                    Modifier.size(DAY_DOT)
                        .clip(CircleShape)
                        .background(
                            when {
                                active && enabled -> MaterialTheme.colorScheme.primary
                                active -> MaterialTheme.colorScheme.outline
                                else -> Color.Transparent
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.narrowName(),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        when {
                            active && enabled -> MaterialTheme.colorScheme.onPrimary
                            active -> MaterialTheme.colorScheme.surface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
private fun scheduleText(alarm: Alarm, now: Instant): String {
    val repeat = repeatText(alarm, now)
    val at = alarm.nextOccurrence ?: return repeat
    if (alarm.isSkipped) return repeat
    return "$repeat · " + stringResource(R.string.alarmlist_in, durationText(now, at))
}

@Composable
private fun repeatText(alarm: Alarm, now: Instant): String {
    val days = alarm.daysOfWeek
    return when {
        days == DaysOfWeek.EVERY_DAY -> stringResource(R.string.alarmlist_daily)
        days == WEEKDAYS -> stringResource(R.string.alarmlist_weekdays)
        days == WEEKENDS -> stringResource(R.string.alarmlist_weekends)
        days.isRepeating ->
            days.toSet().sortedBy { it.ordinal }.joinToString(", ") { it.shortName() }
        else -> onceText(alarm.nextOccurrence, now)
    }
}

@Composable
private fun onceText(at: Instant?, now: Instant): String {
    if (at == null) return stringResource(R.string.alarmlist_once)
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atZone(zone).toLocalDate()
    val day = java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()).atZone(zone).toLocalDate()
    return when (day) {
        today -> stringResource(R.string.alarmlist_once_today)
        today.plusDays(1) -> stringResource(R.string.alarmlist_once_tomorrow)
        else -> stringResource(R.string.alarmlist_once)
    }
}

@Composable
private fun AlarmBadges(alarm: Alarm) {
    val quick = alarm.type == AlarmType.QUICK
    if (!quick && !alarm.isSkipped) return
    Row(
        modifier = Modifier.padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (quick) {
            Badge(
                text = stringResource(R.string.alarmlist_quick_badge),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (alarm.isSkipped) {
            Badge(
                text = stringResource(R.string.alarmlist_skipped_badge),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("alarmlist:skipped:${alarm.id}"),
            )
        }
    }
}

@Composable
private fun Badge(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun AlarmActionsMenu(
    alarm: Alarm,
    canSkip: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    callbacks: AlarmListCallbacks,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (alarm.isSkipped) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.alarmlist_undo_skip)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null) },
                onClick = {
                    onDismiss()
                    callbacks.onUndoSkip(alarm)
                },
            )
        } else if (canSkip) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.alarmlist_skip_once)) },
                leadingIcon = { Icon(Icons.Outlined.EventBusy, contentDescription = null) },
                onClick = {
                    onDismiss()
                    callbacks.onSkip(alarm)
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.alarmlist_sound)) },
            leadingIcon = { Icon(Icons.Outlined.MusicNote, contentDescription = null) },
            onClick = {
                onDismiss()
                callbacks.onPickSound(alarm)
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(R.string.alarmlist_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = {
                onDismiss()
                callbacks.onDelete(alarm)
            },
        )
    }
}

private fun DayOfWeek.shortName(): String =
    java.time.DayOfWeek.of(ordinal + 1)
        .getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())

private fun DayOfWeek.narrowName(): String =
    java.time.DayOfWeek.of(ordinal + 1)
        .getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())

private val WEEKDAYS =
    DaysOfWeek.of(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )
private val WEEKENDS = DaysOfWeek.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private val DAY_DOT = 20.dp
private const val DISABLED_ALPHA = 0.6f
private const val ARMED_SCALE = 1.3f
