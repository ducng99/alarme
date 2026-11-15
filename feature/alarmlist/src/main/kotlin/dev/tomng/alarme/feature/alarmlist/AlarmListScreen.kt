package dev.tomng.alarme.feature.alarmlist

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomng.alarme.core.designsystem.component.ClockTime
import dev.tomng.alarme.core.designsystem.component.clockText
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

    LaunchedEffect(message, messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
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
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { callbacks.onOpenEditor(Alarm.NEW_ID) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.alarmlist_new_alarm)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("alarmlist:new"),
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            HomeHeader(onOpenSettings = callbacks.onOpenSettings)
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("alarmlist:list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (alarms.isNotEmpty()) {
                    item { NextAlarmCard(nextAlarm, hasEnabled = nextAlarm != null) }
                }
                item { QuickCreator(onQuickCreate = callbacks.onQuickCreate) }
                if (alarms.isEmpty()) {
                    item { EmptyState() }
                } else {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmRow(alarm = alarm, callbacks = callbacks)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.alarmlist_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(48.dp).testTag("alarmlist:settings"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.alarmlist_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextAlarmCard(nextAlarm: Alarm?, hasEnabled: Boolean) {
    val title =
        nextAlarm?.let {
            stringResource(R.string.alarmlist_next_alarm, clockText(it.hour, it.minute))
        } ?: stringResource(R.string.alarmlist_all_off)
    val detail =
        nextAlarm?.let { alarm ->
            val relative = relativeTime(alarm)
            val label = descriptor(alarm)
            if (relative == null) label
            else if (label.isEmpty()) relative
            else stringResource(R.string.alarmlist_next_detail, relative, label)
        } ?: stringResource(R.string.alarmlist_all_off_hint)

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (hasEnabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color =
                    if (hasEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor =
                    if (hasEnabled) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector =
                            if (hasEnabled) Icons.Outlined.Alarm else Icons.Outlined.Bedtime,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (hasEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (hasEnabled)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickCreator(onQuickCreate: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
                1 to R.string.alarmlist_quick_1h,
                2 to R.string.alarmlist_quick_2h,
                8 to R.string.alarmlist_quick_8h,
            )
            .forEach { (hours, label) ->
                AssistChip(
                    onClick = { onQuickCreate(hours) },
                    label = { Text(stringResource(label)) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag("alarmlist:quick:$hours"),
                )
            }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
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
private fun AlarmRow(alarm: Alarm, callbacks: AlarmListCallbacks) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable { callbacks.onOpenEditor(alarm.id) }
                .padding(start = 18.dp, end = 6.dp, top = 14.dp, bottom = 14.dp)
                .testTag("alarmlist:card:${alarm.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            ClockTime(
                hour = alarm.hour,
                minute = alarm.minute,
                style = MaterialTheme.typography.displaySmall,
                color =
                    if (alarm.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = descriptor(alarm).ifEmpty { stringResource(R.string.alarmlist_once) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (alarm.type == AlarmType.QUICK) {
                    Text(
                        text = stringResource(R.string.alarmlist_quick_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (alarm.isSkipped) {
                    Text(
                        text = stringResource(R.string.alarmlist_skipped_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier.padding(start = 8.dp).testTag("alarmlist:skipped:${alarm.id}"),
                    )
                }
            }
        }
        Switch(
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

@Composable
private fun AlarmActionsMenu(
    alarm: Alarm,
    expanded: Boolean,
    onDismiss: () -> Unit,
    callbacks: AlarmListCallbacks,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
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
        .getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())

private fun AlarmListMessage.textRes(): Int =
    when (this) {
        AlarmListMessage.SkipDisabled -> R.string.alarmlist_skip_disabled
        AlarmListMessage.SkipNotRepeating -> R.string.alarmlist_skip_not_repeating
        AlarmListMessage.QuickNotEditable -> R.string.alarmlist_quick_not_editable
        AlarmListMessage.NotArmed -> R.string.alarmlist_not_armed
    }
