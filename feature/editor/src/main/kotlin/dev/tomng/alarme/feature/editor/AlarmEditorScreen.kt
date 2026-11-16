package dev.tomng.alarme.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.clockText
import dev.tomng.alarme.core.designsystem.component.rememberFeedback
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.mission.Mission
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/** Removes a mission and offers Undo in the snackbar. */
@Composable
private fun rememberRemoveMission(
    snackbar: SnackbarHostState,
    callbacks: AlarmEditorCallbacks,
): (Int) -> Unit {
    val scope = rememberCoroutineScope()
    val removedText = stringResource(R.string.editor_mission_removed)
    val undoText = stringResource(R.string.editor_undo)
    return { index ->
        callbacks.onRemoveMission(index)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result =
                snackbar.showSnackbar(removedText, undoText, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) callbacks.onRestoreMission()
        }
    }
}

@Composable
private fun DraftErrors(errors: List<DraftError>) {
    if (errors.isEmpty()) return
    Text(
        text = errors.map { stringResource(it.textRes()) }.joinToString("\n"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = Spacing.l, top = Spacing.s).testTag("editor_errors"),
    )
}

/** The sheets and dialogs that float over the editor. */
@Composable
private fun EditorOverlays(
    missions: List<Mission>,
    editingMission: Int?,
    addingMission: Boolean,
    showDiscard: Boolean,
    callbacks: AlarmEditorCallbacks,
    onCloseMission: () -> Unit,
    onCloseAdd: () -> Unit,
    onCloseDiscard: () -> Unit,
    onBack: () -> Unit,
) {
    val feedback = rememberFeedback()
    val mission = editingMission?.let { missions.getOrNull(it) }
    if (editingMission != null && mission != null) {
        MissionConfigSheet(
            mission = mission,
            onDismiss = onCloseMission,
            onConfirm = { updated ->
                callbacks.onReplaceMission(editingMission, updated)
                onCloseMission()
            },
        )
    }
    if (addingMission) {
        AddMissionSheet(
            onDismiss = onCloseAdd,
            onAdd = { added ->
                feedback.confirm()
                callbacks.onAddMission(added)
                onCloseAdd()
            },
        )
    }
    if (showDiscard) {
        DiscardDialog(
            onDiscard = {
                onCloseDiscard()
                onBack()
            },
            onKeepEditing = onCloseDiscard,
        )
    }
}

/**
 * The alarm editor: the time on inline wheels, the repeat days, and every option in segmented
 * groups. Save sits at the bottom within thumb reach; problems are flagged where they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    state: AlarmEditorUiState,
    soundTitle: String?,
    onBack: () -> Unit,
    callbacks: AlarmEditorCallbacks,
    modifier: Modifier = Modifier,
    backupSoundTitle: String? = null,
) {
    var showDiscard by remember { mutableStateOf(false) }
    var editingMission by rememberSaveable { mutableStateOf<Int?>(null) }
    var addingMission by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val feedback = rememberFeedback()
    var rejectTick by remember { mutableIntStateOf(0) }

    val requestBack = { if (state.hasChanges) showDiscard = true else onBack() }
    BackHandler(enabled = state.hasChanges) { showDiscard = true }

    val draft = state.draft
    NotArmedEffect(state.notArmed, snackbar, callbacks.onNotArmedShown)
    LaunchedEffect(state.errors) {
        if (state.errors.isNotEmpty()) {
            feedback.reject()
            rejectTick++
            listState.animateScrollToItem(MISSIONS_ITEM_INDEX)
        }
    }
    val removeMission = rememberRemoveMission(snackbar, callbacks)

    EditorOverlays(
        missions = draft.missions,
        editingMission = editingMission,
        addingMission = addingMission,
        showDiscard = showDiscard,
        callbacks = callbacks,
        onCloseMission = { editingMission = null },
        onCloseAdd = { addingMission = false },
        onCloseDiscard = { showDiscard = false },
        onBack = onBack,
    )

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("editor_screen"),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            EditorTopBar(
                loading = state.loading,
                isNew = draft.isNew,
                summary = clockText(draft.hour, draft.minute),
                onBack = requestBack,
                onDelete = callbacks.onDelete,
            )
        },
        bottomBar = {
            if (!state.loading) {
                SaveBar(
                    saving = state.saving,
                    saved = state.finished != null,
                    rejectTick = rejectTick,
                    onSave = callbacks.onSave,
                )
            }
        },
    ) { innerPadding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("editor_list"),
            contentPadding =
                PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.s,
                    bottom = Spacing.xl,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "time") {
                TimeHero(
                    alarmId = draft.id,
                    hour = draft.hour,
                    minute = draft.minute,
                    nextRing = state.nextRing,
                    onTime = callbacks.onTime,
                    modifier = Modifier.widthIn(max = MAX_WIDTH),
                )
            }
            item(key = "days") {
                DayPicker(
                    days = draft.days,
                    onToggleDay = callbacks.onToggleDay,
                    onDays = callbacks.onDays,
                    modifier = Modifier.widthIn(max = MAX_WIDTH).padding(top = Spacing.l),
                )
            }
            section("label", R.string.editor_section_label) { LabelGroup(draft, callbacks) }
            section(
                "missions",
                R.string.editor_section_missions,
                MissionCount(draft.missions.size),
            ) {
                MissionGroup(
                    draft = draft,
                    highlightIncomplete = state.errors.contains(DraftError.IncompleteMission),
                    onMoveMission = callbacks.onMoveMission,
                    onRemoveMission = removeMission,
                    onEditMission = { editingMission = it },
                    onAddMission = { addingMission = true },
                )
                DraftErrors(state.errors)
            }
            section("sound", R.string.editor_section_sound) {
                SoundGroup(
                    draft = draft,
                    soundTitle = soundTitle,
                    backupSoundTitle = backupSoundTitle,
                    callbacks = callbacks,
                )
            }
            section("wake", R.string.editor_section_wake_up) { WakeUpGroup(draft, callbacks) }
            section("memo", R.string.editor_section_memo) { MemoGroup(draft, callbacks) }
        }
    }
}

private class MissionCount(val count: Int)

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    key: String,
    title: Int,
    count: MissionCount? = null,
    content: @Composable () -> Unit,
) {
    item(key = key) {
        Column(modifier = Modifier.widthIn(max = MAX_WIDTH).fillMaxWidth()) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            start = Spacing.l,
                            end = Spacing.l,
                            top = Spacing.xl,
                            bottom = Spacing.s,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (count != null) {
                    Text(
                        text =
                            stringResource(
                                R.string.editor_missions_count,
                                count.count,
                                Mission.MAX_PER_ALARM,
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun NotArmedEffect(
    notArmed: Boolean,
    snackbar: SnackbarHostState,
    onShown: (fix: Boolean) -> Unit,
) {
    val text = stringResource(R.string.editor_not_armed)
    val fix = stringResource(R.string.editor_fix)
    LaunchedEffect(notArmed) {
        if (notArmed) {
            val result =
                snackbar.showSnackbar(
                    text,
                    fix,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
            onShown(result == SnackbarResult.ActionPerformed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    loading: Boolean,
    isNew: Boolean,
    summary: String,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = {
            if (!loading) {
                Text(
                    stringResource(
                        if (isNew) R.string.editor_title_new else R.string.editor_title_edit,
                    ),
                )
            }
        },
        subtitle = { if (!loading) Text(summary) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back")) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.editor_back))
            }
        },
        actions = {
            if (!loading && !isNew) {
                IconButton(onClick = onDelete, modifier = Modifier.testTag("editor_delete")) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.editor_delete),
                    )
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** The save button: shakes when the draft cannot be saved. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SaveBar(saving: Boolean, saved: Boolean, rejectTick: Int, onSave: () -> Unit) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(rejectTick) {
        if (rejectTick == 0) return@LaunchedEffect
        shake.animateTo(
            0f,
            keyframes {
                durationMillis = SHAKE_MILLIS
                SHAKE_PX at SHAKE_MILLIS / 6
                -SHAKE_PX at SHAKE_MILLIS / 3
                SHAKE_PX / 2 at SHAKE_MILLIS / 2
                -SHAKE_PX / 2 at SHAKE_MILLIS * 2 / 3
            },
        )
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.l),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Button(
                onClick = onSave,
                enabled = !saving && !saved,
                shapes = ButtonDefaults.shapes(),
                modifier =
                    Modifier.widthIn(max = MAX_WIDTH)
                        .fillMaxWidth()
                        .height(ButtonDefaults.MediumContainerHeight)
                        .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
                        .testTag("editor_save"),
            ) {
                Text(
                    stringResource(R.string.editor_save),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
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
    java.time.DayOfWeek.of(day.ordinal + 1)
        .getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())

internal fun DraftError.textRes(): Int =
    when (this) {
        DraftError.TooManyMissions -> R.string.editor_error_too_many_missions
        DraftError.IncompleteMission -> R.string.editor_error_incomplete_mission
    }

private val MAX_WIDTH = 640.dp
private const val MISSIONS_ITEM_INDEX = 3
private const val SHAKE_MILLIS = 400
private const val SHAKE_PX = 12f
