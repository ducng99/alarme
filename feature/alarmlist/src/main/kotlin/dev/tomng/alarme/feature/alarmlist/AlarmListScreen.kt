package dev.tomng.alarme.feature.alarmlist

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomng.alarme.core.designsystem.component.AlarmeScaffold
import dev.tomng.alarme.core.designsystem.component.AlarmeSwitch
import dev.tomng.alarme.core.designsystem.component.ClockTime
import dev.tomng.alarme.core.designsystem.component.MedallionShape
import dev.tomng.alarme.core.designsystem.component.SectionLabel
import dev.tomng.alarme.core.designsystem.component.ShapeMedallion
import dev.tomng.alarme.core.designsystem.component.clockText
import dev.tomng.alarme.core.designsystem.component.rememberListPadding
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AlarmType
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import java.util.Locale
import kotlinx.datetime.DayOfWeek

/** The alarm list: next-alarm summary, alarm rows with actions, and the quick-alarm creator. */
@Composable
fun AlarmListScreen(
    modifier: Modifier = Modifier,
    viewModel: AlarmListViewModel = hiltViewModel(),
    onPickSound: (Alarm) -> Unit = {},
    onPickDefault: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenEditor: (Long) -> Unit = {},
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val nextAlarm by viewModel.nextAlarm.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it.textRes()) }
    val undoLabel = stringResource(R.string.alarmlist_undo)

    LaunchedEffect(message, messageText) {
        if (messageText != null) {
            val undoable = message == AlarmListMessage.Deleted
            val result =
                snackbar.showSnackbar(
                    message = messageText,
                    actionLabel = if (undoable) undoLabel else null,
                    duration = if (undoable) SnackbarDuration.Long else SnackbarDuration.Short,
                )
            if (undoable && result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
            viewModel.consumeMessage()
        }
    }

    AlarmListContent(
        alarms = alarms,
        nextAlarm = nextAlarm,
        snackbarHostState = snackbar,
        callbacks =
            AlarmListCallbacks(
                onToggle = viewModel::setEnabled,
                onSkip = viewModel::skip,
                onUndoSkip = viewModel::undoSkip,
                onDelete = viewModel::delete,
                onQuickCreate = viewModel::createQuick,
                onPickSound = onPickSound,
                onPickDefault = onPickDefault,
                onOpenSettings = onOpenSettings,
                onOpenEditor = onOpenEditor,
            ),
        modifier = modifier,
    )
}

/** The user intents the list emits. */
class AlarmListCallbacks(
    val onToggle: (Alarm, Boolean) -> Unit,
    val onSkip: (Alarm) -> Unit,
    val onUndoSkip: (Alarm) -> Unit,
    val onDelete: (Alarm) -> Unit,
    val onQuickCreate: (Int) -> Unit,
    val onPickSound: (Alarm) -> Unit = {},
    val onPickDefault: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenEditor: (Long) -> Unit = {},
)

@Composable
fun AlarmListContent(
    alarms: List<Alarm>,
    nextAlarm: Alarm?,
    callbacks: AlarmListCallbacks,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    AlarmeScaffold(
        title = stringResource(R.string.alarmlist_title),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(
                onClick = callbacks.onOpenSettings,
                modifier = Modifier.testTag("alarmlist:settings"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.alarmlist_settings),
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.alarmlist_new_alarm)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { callbacks.onOpenEditor(Alarm.NEW_ID) },
                expanded = fabExpanded,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("alarmlist:new"),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("alarmlist:list"),
            contentPadding = rememberListPadding(innerPadding, horizontal = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (alarms.isNotEmpty()) {
                item(key = "next") { NextAlarmCard(nextAlarm, modifier = Modifier.animateItem()) }
            }
            item(key = "quick") {
                QuickCreator(
                    onQuickCreate = callbacks.onQuickCreate,
                    modifier = Modifier.animateItem(),
                )
            }
            if (alarms.isEmpty()) {
                item(key = "empty") { EmptyState(modifier = Modifier.animateItem()) }
            } else {
                items(alarms, key = { it.id }) { alarm ->
                    SwipeableAlarmCard(
                        alarm = alarm,
                        callbacks = callbacks,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NextAlarmCard(nextAlarm: Alarm?, modifier: Modifier = Modifier) {
    val armed = nextAlarm != null
    val container by
        animateColorAsState(
            if (armed) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            label = "nextContainer",
        )
    val onContainer =
        if (armed) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = onContainer,
    ) {
        AnimatedContent(
            targetState = nextAlarm,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentKey = { it?.let { alarm -> alarm.id to alarm.nextOccurrence } },
            label = "nextAlarm",
        ) { alarm ->
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapeMedallion(
                    shape = MedallionShape.Cookie,
                    size = 56.dp,
                    color =
                        if (alarm != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Icon(
                        imageVector =
                            if (alarm != null) Icons.Outlined.Alarm else Icons.Outlined.Bedtime,
                        contentDescription = null,
                        tint =
                            if (alarm != null) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            alarm?.let {
                                stringResource(
                                    R.string.alarmlist_next_alarm,
                                    clockText(it.hour, it.minute),
                                )
                            } ?: stringResource(R.string.alarmlist_all_off),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text =
                            alarm?.let { nextDetail(it) }
                                ?: stringResource(R.string.alarmlist_all_off_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer.copy(alpha = 0.78f),
                    )
                }
            }
        }
    }
}

@Composable
private fun nextDetail(alarm: Alarm): String {
    val relative = relativeTime(alarm)
    val label = descriptor(alarm)
    return when {
        relative == null -> label
        label.isEmpty() -> relative
        else -> stringResource(R.string.alarmlist_next_detail, relative, label)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickCreator(onQuickCreate: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(
            text = stringResource(R.string.alarmlist_quick_title),
            modifier = Modifier.padding(start = 0.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                    1 to R.string.alarmlist_quick_1h,
                    2 to R.string.alarmlist_quick_2h,
                    8 to R.string.alarmlist_quick_8h,
                )
                .forEach { (hours, label) ->
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onQuickCreate(hours)
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).testTag("alarmlist:quick:$hours"),
                    ) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                    }
                }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapeMedallion(shape = MedallionShape.Sunny, size = 128.dp, rotating = true) {
            Icon(
                imageVector = Icons.Outlined.Bedtime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.alarmlist_no_alarms),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.alarmlist_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SwipeableAlarmCard(
    alarm: Alarm,
    callbacks: AlarmListCallbacks,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                callbacks.onDelete(alarm)
            }
        },
        backgroundContent = { DeleteBackground(progress = dismissState.progress) },
    ) {
        AlarmCard(alarm = alarm, callbacks = callbacks)
    }
}

@Composable
private fun DeleteBackground(progress: Float) {
    val iconScale by animateFloatAsState(if (progress > 0.25f) 1.15f else 0.85f, label = "trash")
    Box(
        modifier =
            Modifier.fillMaxSize()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 28.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.alarmlist_swipe_delete),
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, callbacks: AlarmListCallbacks) {
    var menuOpen by remember { mutableStateOf(false) }
    val container by
        animateColorAsState(
            if (alarm.enabled) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow,
            label = "cardContainer",
        )
    val timeColor by
        animateColorAsState(
            if (alarm.enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            label = "timeColor",
        )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(onClickLabel = stringResource(R.string.alarmlist_edit)) {
                        callbacks.onOpenEditor(alarm.id)
                    }
                    .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp)
                    .testTag("alarmlist:card:${alarm.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ClockTime(
                    hour = alarm.hour,
                    minute = alarm.minute,
                    style = MaterialTheme.typography.displaySmall,
                    color = timeColor,
                )
                Text(
                    text = descriptor(alarm).ifEmpty { stringResource(R.string.alarmlist_once) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AlarmBadges(alarm)
            }
            AlarmeSwitch(
                checked = alarm.enabled,
                onCheckedChange = { callbacks.onToggle(alarm, it) },
                modifier = Modifier.testTag("alarmlist:toggle:${alarm.id}"),
            )
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag("alarmlist:menu:${alarm.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.alarmlist_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AlarmActionsMenu(
                    alarm = alarm,
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    callbacks = callbacks,
                )
            }
        }
    }
}

@Composable
private fun AlarmBadges(alarm: Alarm) {
    val quick = alarm.type == AlarmType.QUICK
    if (!quick && !alarm.isSkipped) return
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.testTag("alarmlist:skipped:${alarm.id}"),
            )
        }
    }
}

@Composable
private fun Badge(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AlarmActionsMenu(
    alarm: Alarm,
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
                leadingIcon = { Icon(Icons.Outlined.Undo, contentDescription = null) },
                onClick = {
                    onDismiss()
                    callbacks.onUndoSkip(alarm)
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.alarmlist_skip_once)) },
                leadingIcon = { Icon(Icons.Outlined.Snooze, contentDescription = null) },
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
                    text = stringResource(R.string.alarmlist_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
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

private fun relativeTime(alarm: Alarm): String? {
    val occurrence = alarm.nextOccurrence ?: return null
    return DateUtils.getRelativeTimeSpanString(
            occurrence.toEpochMilliseconds(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        )
        .toString()
        .replaceFirstChar { it.lowercase(Locale.getDefault()) }
}

@Composable
private fun descriptor(alarm: Alarm): String {
    val days = daysText(alarm.daysOfWeek)
    return when {
        alarm.name.isBlank() -> days
        days.isEmpty() -> alarm.name
        else -> "${alarm.name} · $days"
    }
}

@Composable
private fun daysText(days: DaysOfWeek): String =
    when {
        days == DaysOfWeek.EVERY_DAY -> stringResource(R.string.alarmlist_daily)
        !days.isRepeating -> stringResource(R.string.alarmlist_once)
        else -> days.toSet().joinToString(" ") { it.shortName() }
    }

private fun DayOfWeek.shortName(): String =
    java.time.DayOfWeek.of(ordinal + 1)
        .getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())

private fun AlarmListMessage.textRes(): Int =
    when (this) {
        AlarmListMessage.SkipDisabled -> R.string.alarmlist_skip_disabled
        AlarmListMessage.SkipNotRepeating -> R.string.alarmlist_skip_not_repeating
        AlarmListMessage.QuickNotEditable -> R.string.alarmlist_quick_not_editable
        AlarmListMessage.NotArmed -> R.string.alarmlist_not_armed
        AlarmListMessage.Deleted -> R.string.alarmlist_deleted
    }
