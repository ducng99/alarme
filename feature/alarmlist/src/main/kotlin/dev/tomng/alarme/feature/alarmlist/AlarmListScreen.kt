package dev.tomng.alarme.feature.alarmlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AlarmType
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.DayOfWeek

/** The alarm list: next-alarm header, alarm cards with badges, and the quick-alarm creator. */
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Text(
                text =
                    nextAlarm?.let { stringResource(R.string.alarmlist_next_alarm, timeText(it)) }
                        ?: stringResource(
                            if (alarms.isEmpty()) R.string.alarmlist_no_alarms
                            else R.string.alarmlist_all_off,
                        ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("alarmlist:next"),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = callbacks.onPickDefault) {
                    Text(stringResource(R.string.alarmlist_default_sound))
                }
                TextButton(
                    onClick = callbacks.onOpenSettings,
                    modifier = Modifier.testTag("alarmlist:settings"),
                ) {
                    Text(stringResource(R.string.alarmlist_settings))
                }
            }
            QuickCreator(onQuickCreate = callbacks.onQuickCreate)
            TextButton(
                onClick = { callbacks.onOpenEditor(Alarm.NEW_ID) },
                modifier = Modifier.testTag("alarmlist:new"),
            ) {
                Text(stringResource(R.string.alarmlist_new_alarm))
            }
            if (alarms.isEmpty()) {
                Text(
                    text = stringResource(R.string.alarmlist_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("alarmlist:list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmCard(alarm = alarm, callbacks = callbacks)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCreator(onQuickCreate: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
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
                    modifier = Modifier.testTag("alarmlist:quick:$hours"),
                )
            }
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, callbacks: AlarmListCallbacks) {
    Card(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { callbacks.onOpenEditor(alarm.id) }
                .testTag("alarmlist:card:${alarm.id}"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = timeText(alarm), style = MaterialTheme.typography.headlineMedium)
                    if (alarm.name.isNotBlank()) {
                        Text(text = alarm.name, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        text = daysText(alarm.daysOfWeek),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (alarm.type == AlarmType.QUICK) {
                        Text(
                            text = stringResource(R.string.alarmlist_quick_badge),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (alarm.isSkipped) {
                        Text(
                            text = stringResource(R.string.alarmlist_skipped_badge),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.testTag("alarmlist:skipped:${alarm.id}"),
                        )
                    }
                }
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = { callbacks.onToggle(alarm, it) },
                    modifier = Modifier.testTag("alarmlist:toggle:${alarm.id}"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (alarm.isSkipped) {
                    TextButton(onClick = { callbacks.onUndoSkip(alarm) }) {
                        Text(stringResource(R.string.alarmlist_undo_skip))
                    }
                } else {
                    TextButton(onClick = { callbacks.onSkip(alarm) }) {
                        Text(stringResource(R.string.alarmlist_skip_once))
                    }
                }
                TextButton(onClick = { callbacks.onPickSound(alarm) }) {
                    Text(stringResource(R.string.alarmlist_sound))
                }
                TextButton(onClick = { callbacks.onDelete(alarm) }) {
                    Text(stringResource(R.string.alarmlist_delete))
                }
            }
        }
    }
}

private val timeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

private fun timeText(alarm: Alarm): String =
    timeFormat.format(java.time.LocalTime.of(alarm.hour, alarm.minute))

@Composable
private fun daysText(days: DaysOfWeek): String =
    when {
        days == DaysOfWeek.EVERY_DAY -> stringResource(R.string.alarmlist_daily)
        !days.isRepeating -> stringResource(R.string.alarmlist_once)
        else -> days.toSet().joinToString(", ") { it.shortName() }
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
    }
