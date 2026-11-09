package dev.tomng.alarme.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.MissionType
import dev.tomng.alarme.feature.ringtones.PickerMode
import dev.tomng.alarme.feature.ringtones.RingtonePickerScreen
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.datetime.DayOfWeek

/** The alarm editor: every per-alarm option, the mission chain, and save/delete. */
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
    if (showTimePicker) {
        TimePickerDialog(
            hour = draft.hour,
            minute = draft.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                callbacks.onTime(hour, minute)
                showTimePicker = false
            },
        )
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

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("editor_screen"),
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
        EditorList(
            draft = draft,
            soundTitle = soundTitle,
            errorText = errorText,
            callbacks = callbacks,
            onEditTime = { showTimePicker = true },
            onEditMission = { editingMission = it },
            onOpenSound = { showPicker = true },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun EditorList(
    draft: AlarmDraft,
    soundTitle: String?,
    errorText: String,
    callbacks: AlarmEditorCallbacks,
    onEditTime: () -> Unit,
    onEditMission: (Int) -> Unit,
    onOpenSound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("editor_list"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { TimeRow(hour = draft.hour, minute = draft.minute, onClick = onEditTime) }
        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = callbacks.onName,
                label = { Text(stringResource(R.string.editor_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("editor_name"),
            )
        }
        item { EmojiRow(selected = draft.emoji, onSelect = callbacks.onEmoji) }
        item { RepeatSection(draft = draft, callbacks = callbacks) }
        item {
            SoundRow(
                title = soundTitle ?: stringResource(R.string.editor_sound_default),
                onClick = onOpenSound,
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item { RingOptions(draft = draft, callbacks = callbacks) }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item { SnoozeSection(draft = draft, callbacks = callbacks) }
        item { WakeUpCheckSection(draft = draft, callbacks = callbacks) }
        item { MemoSection(draft = draft, callbacks = callbacks) }
        item { ExtrasSection(draft = draft, callbacks = callbacks) }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item {
            SectionTitle(
                text =
                    stringResource(
                        R.string.editor_missions,
                        draft.missions.size,
                        Mission.MAX_PER_ALARM,
                    ),
            )
        }
        itemsIndexed(draft.missions) { index, mission ->
            MissionRow(
                index = index,
                mission = mission,
                onEdit = { onEditMission(index) },
                onRemove = { callbacks.onRemoveMission(index) },
            )
        }
        item { AddMissionRow(enabled = draft.canAddMission, onAdd = callbacks.onAddMission) }
        if (errorText.isNotEmpty()) {
            item {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp).testTag("editor_errors"),
                )
            }
        }
    }
}

/** The editor's user intents. */
class AlarmEditorCallbacks(
    val onTime: (hour: Int, minute: Int) -> Unit = { _, _ -> },
    val onName: (String) -> Unit = {},
    val onEmoji: (String) -> Unit = {},
    val onToggleDay: (DayOfWeek) -> Unit = {},
    val onEveryDay: () -> Unit = {},
    val onOneShot: () -> Unit = {},
    val onRingtone: (String?) -> Unit = {},
    val onEnabled: (Boolean) -> Unit = {},
    val onVibrate: (Boolean) -> Unit = {},
    val onVolume: (Float) -> Unit = {},
    val onCrescendo: (Boolean) -> Unit = {},
    val onCrescendoSeconds: (Int) -> Unit = {},
    val onSnoozeEnabled: (Boolean) -> Unit = {},
    val onSnoozeMinutes: (Int) -> Unit = {},
    val onSnoozeLimit: (Int) -> Unit = {},
    val onWakeUpCheckMinutes: (Int) -> Unit = {},
    val onMemo: (String) -> Unit = {},
    val onTts: (Boolean) -> Unit = {},
    val onBackupSound: (Boolean) -> Unit = {},
    val onTimeAnnouncement: (Boolean) -> Unit = {},
    val onAddMission: (MissionType) -> Unit = {},
    val onRemoveMission: (Int) -> Unit = {},
    val onReplaceMission: (Int, Mission) -> Unit = { _, _ -> },
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onNotArmedShown: () -> Unit = {},
)

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
                stringResource(
                    if (isNew) R.string.editor_title_new else R.string.editor_title_edit,
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back")) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.editor_back),
                )
            }
        },
        actions = {
            if (!isNew) {
                IconButton(onClick = onDelete, modifier = Modifier.testTag("editor_delete")) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.editor_delete),
                    )
                }
            }
            TextButton(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.testTag("editor_save"),
            ) {
                Text(stringResource(R.string.editor_save))
            }
        },
    )
}

@Composable
private fun TimeRow(hour: Int, minute: Int, onClick: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp)
                .testTag("editor_time"),
    ) {
        Text(
            text = formatTime(hour, minute),
            style = MaterialTheme.typography.displayMedium,
        )
        Text(
            text = stringResource(R.string.editor_time_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EmojiRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EMOJIS.forEach { emoji ->
            FilterChip(
                selected = emoji == selected,
                onClick = { onSelect(emoji) },
                label = { Text(emoji) },
                modifier = Modifier.testTag("editor_emoji:$emoji"),
            )
        }
    }
}

@Composable
private fun RepeatSection(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SectionTitle(stringResource(R.string.editor_repeat))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in draft.days,
                    onClick = { callbacks.onToggleDay(day) },
                    label = { Text(dayLabel(day)) },
                    modifier = Modifier.testTag("editor_day:${day.name}"),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = callbacks.onEveryDay,
                label = { Text(stringResource(R.string.editor_every_day)) },
                modifier = Modifier.testTag("editor_every_day"),
            )
            AssistChip(
                onClick = callbacks.onOneShot,
                label = { Text(stringResource(R.string.editor_once)) },
                modifier = Modifier.testTag("editor_once"),
            )
        }
    }
}

@Composable
private fun SoundRow(title: String, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp)
                .testTag("editor_sound"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.editor_sound),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MissionRow(
    index: Int,
    mission: Mission,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val incomplete = !mission.isComplete()
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(vertical = 10.dp)
                .testTag("editor_mission:$index"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    stringResource(
                        R.string.editor_mission_slot,
                        index + 1,
                        missionTypeLabel(mission),
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    if (incomplete) stringResource(R.string.editor_mission_incomplete)
                    else missionSummary(mission),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (incomplete) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onRemove,
            modifier = Modifier.testTag("editor_mission_remove:$index"),
        ) {
            Text(stringResource(R.string.editor_mission_remove))
        }
    }
}

@Composable
private fun AddMissionRow(enabled: Boolean, onAdd: (MissionType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        TextButton(
            onClick = { expanded = !expanded },
            enabled = enabled,
            modifier = Modifier.testTag("editor_add_mission"),
        ) {
            Text(stringResource(R.string.editor_add_mission))
        }
        if (expanded) {
            MissionType.entries.forEach { type ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable {
                                onAdd(type)
                                expanded = false
                            }
                            .padding(vertical = 10.dp)
                            .testTag("editor_mission_type:${type.name}"),
                ) {
                    Text(text = missionTypeLabel(type), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pickerState.hour, pickerState.minute) },
                modifier = Modifier.testTag("editor_time_ok"),
            ) {
                Text(stringResource(R.string.editor_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
        },
        text = { TimePicker(state = pickerState) },
    )
}

private val EMOJIS = listOf("⏰", "🌅", "☕", "🏃", "📚", "💪", "🐱", "🎵")

internal fun formatTime(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute)
        .format(
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()),
        )

internal fun dayLabel(day: DayOfWeek): String =
    java.time.DayOfWeek.of(day.isoDayNumber)
        .getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())

internal fun DraftError.textRes(): Int =
    when (this) {
        DraftError.TooManyMissions -> R.string.editor_error_too_many_missions
        DraftError.IncompleteMission -> R.string.editor_error_incomplete_mission
    }

@Composable
internal fun missionTypeLabel(mission: Mission): String =
    stringResource(
        when (mission) {
            is Mission.Math -> R.string.mission_math
            is Mission.Memory -> R.string.mission_memory
            is Mission.Shake -> R.string.mission_shake
            is Mission.Step -> R.string.mission_step
            is Mission.Squat -> R.string.mission_squat
            is Mission.Typing -> R.string.mission_typing
            is Mission.Barcode -> R.string.mission_barcode
            is Mission.Photo -> R.string.mission_photo
            is Mission.ImageRecognition -> R.string.mission_image_recognition
            is Mission.WordBeat -> R.string.mission_word_beat
        },
    )

@Composable
internal fun missionTypeLabel(type: MissionType): String =
    stringResource(
        when (type) {
            MissionType.MATH -> R.string.mission_math
            MissionType.MEMORY -> R.string.mission_memory
            MissionType.SHAKE -> R.string.mission_shake
            MissionType.STEP -> R.string.mission_step
            MissionType.SQUAT -> R.string.mission_squat
            MissionType.TYPING -> R.string.mission_typing
            MissionType.BARCODE -> R.string.mission_barcode
            MissionType.PHOTO -> R.string.mission_photo
            MissionType.IMAGE_RECOGNITION -> R.string.mission_image_recognition
            MissionType.WORD_BEAT -> R.string.mission_word_beat
        },
    )

/** Whether the mission has everything it needs to run. */
internal fun Mission.isComplete(): Boolean =
    when (this) {
        is Mission.Math -> true
        is Mission.Memory -> true
        is Mission.Shake -> count > 0
        is Mission.Step -> count > 0
        is Mission.Squat -> count > 0
        is Mission.Typing -> typingSentences.isNotEmpty()
        is Mission.Barcode -> barcodeValue.isNotBlank()
        is Mission.Photo -> photoPath.isNotBlank()
        is Mission.ImageRecognition -> objects.isNotEmpty()
        is Mission.WordBeat -> selectedRoundSetIds.isNotEmpty()
    }

@Composable
internal fun missionSummary(mission: Mission): String =
    when (mission) {
        is Mission.Math ->
            stringResource(R.string.summary_rounds_level, mission.rounds, mission.difficulty)
        is Mission.Memory ->
            stringResource(R.string.summary_rounds_level, mission.rounds, mission.difficulty)
        is Mission.Shake -> stringResource(R.string.summary_count, mission.count)
        is Mission.Step -> stringResource(R.string.summary_count, mission.count)
        is Mission.Squat -> stringResource(R.string.summary_count, mission.count)
        is Mission.Typing ->
            stringResource(R.string.summary_sentences, mission.typingSentences.size)
        is Mission.Barcode -> stringResource(R.string.summary_barcode)
        is Mission.Photo -> stringResource(R.string.summary_photo)
        is Mission.ImageRecognition ->
            stringResource(R.string.summary_objects, mission.objects.size)
        is Mission.WordBeat ->
            stringResource(
                R.string.summary_word_beat,
                stringResource(languageLabel(mission.language)),
                mission.rounds,
            )
    }

internal fun languageLabel(language: dev.tomng.alarme.core.model.mission.WordBeatLanguage): Int =
    when (language) {
        dev.tomng.alarme.core.model.mission.WordBeatLanguage.ENGLISH -> R.string.language_english
        dev.tomng.alarme.core.model.mission.WordBeatLanguage.KOREAN -> R.string.language_korean
        dev.tomng.alarme.core.model.mission.WordBeatLanguage.JAPANESE -> R.string.language_japanese
    }

private val DayOfWeek.isoDayNumber: Int
    get() = ordinal + 1
