package dev.tomng.alarme.feature.editor

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tomng.alarme.core.designsystem.component.ClockTime
import dev.tomng.alarme.core.designsystem.component.SectionLabel
import dev.tomng.alarme.core.designsystem.component.clockText
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.ringtones.PickerMode
import dev.tomng.alarme.feature.ringtones.RingtonePickerScreen
import java.util.Locale
import kotlin.time.Instant
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
    var showDiscard by remember { mutableStateOf(false) }
    var editingMission by remember { mutableStateOf<Int?>(null) }

    if (showPicker) {
        BackHandler { showPicker = false }
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

    val requestBack = { if (state.hasChanges) showDiscard = true else onBack() }
    BackHandler(enabled = state.hasChanges) { showDiscard = true }

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
    if (showTimePicker) {
        TimePickerSheet(
            hour = draft.hour,
            minute = draft.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                callbacks.onTime(hour, minute)
                showTimePicker = false
            },
        )
    }
    if (showDiscard) {
        DiscardDialog(
            onDiscard = {
                showDiscard = false
                onBack()
            },
            onKeepEditing = { showDiscard = false },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("editor_screen"),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            EditorTopBar(
                isNew = draft.isNew,
                saving = state.saving,
                onBack = requestBack,
                onSave = callbacks.onSave,
                onDelete = callbacks.onDelete,
            )
        },
    ) { innerPadding ->
        EditorBody(
            draft = draft,
            nextRing = state.nextRing,
            soundTitle = soundTitle,
            errorText = errorText,
            callbacks = callbacks,
            innerPadding = innerPadding,
            onOpenSound = { showPicker = true },
            onOpenTime = { showTimePicker = true },
            onEditMission = { editingMission = it },
        )
    }
}

@Composable
private fun EditorBody(
    draft: AlarmDraft,
    nextRing: Instant?,
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
        item { TimeHero(draft.hour, draft.minute, nextRing = nextRing, onOpenTime = onOpenTime) }
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
        item { Spacer(Modifier.height(8.dp)) }
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
        item {
            AnimatedVisibility(visible = errorText.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier =
                            Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                                .testTag("editor_errors"),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back")) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.editor_back))
            }
        },
        actions = {
            if (!isNew) {
                IconButton(onClick = onDelete, modifier = Modifier.testTag("editor_delete")) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.editor_delete),
                    )
                }
            }
            Button(
                onClick = onSave,
                enabled = !saving,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(start = 4.dp, end = 12.dp).testTag("editor_save"),
            ) {
                Text(stringResource(R.string.editor_save))
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun TimeHero(hour: Int, minute: Int, nextRing: Instant?, onOpenTime: () -> Unit) {
    val hint = stringResource(R.string.editor_time_hint)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable(onClickLabel = hint, role = Role.Button, onClick = onOpenTime)
                    .padding(vertical = 28.dp, horizontal = 16.dp)
                    .testTag("editor_time"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ClockTime(
                hour = hour,
                minute = minute,
                style =
                    MaterialTheme.typography.displayLarge.copy(
                        fontSize = 76.sp,
                        lineHeight = 80.sp,
                        letterSpacing = (-1.5).sp,
                    ),
            )
            Text(
                text = ringsText(nextRing),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp).animateContentSize(),
            )
        }
    }
}

@Composable
private fun ringsText(nextRing: Instant?): String {
    if (nextRing == null) return stringResource(R.string.editor_rings_off)
    val locale = LocalLocale.current.platformLocale
    val relative =
        DateUtils.getRelativeTimeSpanString(
                nextRing.toEpochMilliseconds(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
            .toString()
            .replaceFirstChar { it.lowercase(locale) }
    return stringResource(R.string.editor_rings_in, relative)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayPicker(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            DayOfWeek.entries.forEachIndexed { index, day ->
                val label = fullDayLabel(day)
                ToggleButton(
                    checked = day in draft.days,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        callbacks.onToggleDay(day)
                    },
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            DayOfWeek.entries.lastIndex ->
                                ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    contentPadding = PaddingValues(0.dp),
                    modifier =
                        Modifier.weight(1f)
                            .height(52.dp)
                            .semantics { contentDescription = label }
                            .testTag("editor_day:${day.name}"),
                ) {
                    Text(text = dayLabel(day), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            PresetChip(
                text = stringResource(R.string.editor_every_day),
                selected = draft.days.size == DayOfWeek.entries.size,
                onClick = callbacks.onEveryDay,
                tag = "editor_every_day",
            )
            PresetChip(
                text = stringResource(R.string.editor_once),
                selected = draft.days.isEmpty(),
                onClick = callbacks.onOneShot,
                tag = "editor_once",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PresetChip(text: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        shapes =
            ToggleButtonShapes(CircleShape, MaterialTheme.shapes.small, MaterialTheme.shapes.small),
        colors =
            ToggleButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        modifier = Modifier.testTag(tag),
    ) {
        Text(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
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
    TimePickerDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("editor_time_page"),
        title = { Text(stringResource(R.string.editor_pick_time)) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pickerState.hour, pickerState.minute) },
                modifier = Modifier.testTag("editor_time_ok"),
            ) {
                Text(stringResource(R.string.editor_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("editor_time_cancel")) {
                Text(stringResource(R.string.editor_cancel))
            }
        },
    ) {
        TimePicker(state = pickerState)
    }
}

@Composable
private fun DiscardDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.editor_discard_title)) },
        text = { Text(stringResource(R.string.editor_discard_body)) },
        confirmButton = {
            TextButton(onClick = onDiscard, modifier = Modifier.testTag("editor_discard")) {
                Text(stringResource(R.string.editor_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text(stringResource(R.string.editor_keep_editing))
            }
        },
    )
}

internal fun formatTime(hour: Int, minute: Int): String = clockText(hour, minute)

internal fun dayLabel(day: DayOfWeek): String =
    java.time.DayOfWeek.of(day.isoDayNumber)
        .getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())

private fun fullDayLabel(day: DayOfWeek): String =
    java.time.DayOfWeek.of(day.isoDayNumber)
        .getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())

internal fun DraftError.textRes(): Int =
    when (this) {
        DraftError.TooManyMissions -> R.string.editor_error_too_many_missions
        DraftError.IncompleteMission -> R.string.editor_error_incomplete_mission
    }

private val DayOfWeek.isoDayNumber: Int
    get() = ordinal + 1
