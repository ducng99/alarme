package dev.tomng.alarme.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.ClockTime
import dev.tomng.alarme.core.designsystem.component.SectionLabel
import dev.tomng.alarme.core.designsystem.component.clockText
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.ringtones.PickerMode
import dev.tomng.alarme.feature.ringtones.RingtonePickerScreen
import java.util.Locale
import kotlinx.datetime.DayOfWeek

/** The alarm editor: the time, the repeat days, and every per-alarm option grouped into cards. */
@Composable
fun AlarmEditorScreen(
    state: AlarmEditorUiState,
    soundTitle: String?,
    onBack: () -> Unit,
    callbacks: AlarmEditorCallbacks,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var editingMission by remember { mutableStateOf<Int?>(null) }

    if (showPicker) {
        RingtonePickerScreen(
            mode = PickerMode.Alarm,
            selectedUri = state.draft.ringtoneUri,
            onPersist = { uri, _ ->
                callbacks.onRingtone(uri)
                showPicker = false
            },
            onDone = { showPicker = false },
            modifier = modifier,
        )
        return
    }

    val draft = state.draft
    val errorText = state.errors.map { stringResource(it.textRes()) }.joinToString("\n")
    val snackbar = remember { SnackbarHostState() }
    val notArmedText = stringResource(R.string.editor_not_armed)
    LaunchedEffect(state.notArmed) {
        if (state.notArmed) {
            snackbar.showSnackbar(notArmedText)
            callbacks.onNotArmedShown()
        }
    }
    editingMission?.let { index ->
        draft.missions.getOrNull(index)?.let { mission ->
            MissionConfigDialog(
                mission = mission,
                onDismiss = { editingMission = null },
                onConfirm = { updated ->
                    callbacks.onReplaceMission(index, updated)
                    editingMission = null
                },
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("editor_screen"),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                EditorTopBar(
                    isNew = draft.isNew,
                    saving = state.saving,
                    onBack = onBack,
                    onSave = callbacks.onSave,
                    onDelete = callbacks.onDelete,
                )
            },
        ) { innerPadding ->
            EditorBody(
                draft = draft,
                soundTitle = soundTitle,
                errorText = errorText,
                callbacks = callbacks,
                innerPadding = innerPadding,
                onOpenSound = { showPicker = true },
                onOpenTime = { showTimePicker = true },
                onEditMission = { editingMission = it },
            )
        }
        if (showTimePicker) {
            TimePickerPage(
                hour = draft.hour,
                minute = draft.minute,
                onDismiss = { showTimePicker = false },
                onConfirm = { hour, minute ->
                    callbacks.onTime(hour, minute)
                    showTimePicker = false
                },
            )
        }
    }
}

@Composable
private fun EditorBody(
    draft: AlarmDraft,
    soundTitle: String?,
    errorText: String,
    callbacks: AlarmEditorCallbacks,
    innerPadding: PaddingValues,
    onOpenSound: () -> Unit,
    onOpenTime: () -> Unit,
    onEditMission: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("editor_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { TimeHero(draft.hour, draft.minute, onOpenTime = onOpenTime) }
        item { DayPicker(draft = draft, callbacks = callbacks) }
        item { SectionLabel(stringResource(R.string.editor_section_details)) }
        item {
            DetailsGroup(
                draft = draft,
                soundTitle = soundTitle,
                callbacks = callbacks,
                onOpenSound = onOpenSound,
            )
        }
        item { SectionLabel(stringResource(R.string.editor_section_sound)) }
        item { SoundGroup(draft = draft, callbacks = callbacks) }
        item { SectionLabel(stringResource(R.string.editor_section_wake_up)) }
        item { SnoozeGroup(draft = draft, callbacks = callbacks) }
        item { WakeUpCheckSection(draft = draft, callbacks = callbacks) }
        item {
            SectionLabel(
                stringResource(
                    R.string.editor_missions,
                    draft.missions.size,
                    Mission.MAX_PER_ALARM,
                ),
            )
        }
        item {
            MissionGroup(
                draft = draft,
                callbacks = callbacks,
                onEditMission = onEditMission,
            )
        }
        item { SectionLabel(stringResource(R.string.editor_section_memo)) }
        item { MemoSection(draft = draft, callbacks = callbacks) }
        if (errorText.isNotEmpty()) {
            item {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                            .testTag("editor_errors"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    isNew: Boolean,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text =
                    stringResource(
                        if (isNew) R.string.editor_title_new else R.string.editor_title_edit,
                    ),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back")) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.editor_back),
                )
            }
        },
        actions = {
            if (!isNew) {
                IconButton(onClick = onDelete, modifier = Modifier.testTag("editor_delete")) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.editor_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledIconButton(
                onClick = onSave,
                enabled = !saving,
                modifier =
                    Modifier.padding(start = 4.dp, end = 8.dp).size(40.dp).testTag("editor_save"),
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.editor_save),
                )
            }
        },
        colors =
            androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
private fun TimeHero(hour: Int, minute: Int, onOpenTime: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = 4.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onOpenTime)
                .padding(vertical = 12.dp)
                .testTag("editor_time"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ClockTime(
            hour = hour,
            minute = minute,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.editor_time_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayPicker(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DayOfWeek.entries.forEach { day ->
                DayToggle(
                    day = day,
                    selected = day in draft.days,
                    onClick = { callbacks.onToggleDay(day) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(
                onClick = callbacks.onEveryDay,
                modifier = Modifier.testTag("editor_every_day"),
            ) {
                Text(stringResource(R.string.editor_every_day))
            }
            TextButton(
                onClick = callbacks.onOneShot,
                modifier = Modifier.testTag("editor_once"),
            ) {
                Text(stringResource(R.string.editor_once))
            }
        }
    }
}

@Composable
private fun DayToggle(day: DayOfWeek, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(44.dp).testTag("editor_day:${day.name}"),
        shape = CircleShape,
        color =
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor =
            if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(
            modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dayLabel(day),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerPage(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val pickerState =
        rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
        )
    Surface(
        modifier = Modifier.fillMaxSize().testTag("editor_time_page"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("editor_time_cancel"),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.editor_time_cancel),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.editor_pick_time),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.weight(1f))
            TimePicker(state = pickerState)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onConfirm(pickerState.hour, pickerState.minute) },
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("editor_time_ok"),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = stringResource(R.string.editor_ok),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

internal fun formatTime(hour: Int, minute: Int): String = clockText(hour, minute)

internal fun dayLabel(day: DayOfWeek): String =
    java.time.DayOfWeek.of(day.isoDayNumber)
        .getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())

internal fun DraftError.textRes(): Int =
    when (this) {
        DraftError.TooManyMissions -> R.string.editor_error_too_many_missions
        DraftError.IncompleteMission -> R.string.editor_error_incomplete_mission
    }

private val DayOfWeek.isoDayNumber: Int
    get() = ordinal + 1
