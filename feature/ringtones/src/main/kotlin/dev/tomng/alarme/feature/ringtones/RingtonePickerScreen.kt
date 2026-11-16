package dev.tomng.alarme.feature.ringtones

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomng.alarme.core.designsystem.component.AlarmeScaffold
import dev.tomng.alarme.core.designsystem.component.EmptyState
import dev.tomng.alarme.core.designsystem.component.SectionHeader
import dev.tomng.alarme.core.designsystem.component.rememberListPadding
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import dev.tomng.alarme.core.model.ringtone.RingtoneUris

/** Whether the picker edits one alarm's sound, the backup sound, or the app-wide default. */
enum class PickerMode {
    Alarm,
    Default,
    Backup,
}

/** The picker's user intents; hosts persist the selection. */
class RingtonePickerCallbacks(
    val onSelect: (uri: String?, title: String) -> Unit,
    val onSetDefault: (row: CatalogRingtone) -> Unit,
    val onImportClick: () -> Unit,
    val onRecordToggle: () -> Unit,
    val onPreview: (uri: String) -> Unit,
    val onDelete: (row: CatalogRingtone) -> Unit,
    val onDone: () -> Unit,
)

@Composable
fun RingtonePickerScreen(
    mode: PickerMode,
    selectedUri: String?,
    onPersist: (uri: String?, title: String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RingtonePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    DisposableEffect(viewModel) { onDispose { viewModel.stopPreview() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopPreview() }
    PickerMessageEffect(
        message = state.message,
        snackbar = snackbar,
        onUndo = viewModel::undoDelete,
        onDeleteSettled = viewModel::commitDelete,
        onConsumed = viewModel::consumeMessage,
    )

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            viewModel.import(uris.map { it.toString() })
        }

    RingtonePickerContent(
        state = state,
        mode = mode,
        selectedUri = selectedUri,
        snackbarHostState = snackbar,
        callbacks =
            RingtonePickerCallbacks(
                onSelect = { uri, title ->
                    if (uri == null || uri == RingtoneUris.SILENT || uri == RingtoneUris.RANDOM) {
                        viewModel.stopPreview()
                    } else {
                        viewModel.preview(uri)
                    }
                    onPersist(uri, title)
                },
                onSetDefault = { row ->
                    viewModel.setDefault(row)
                    viewModel.preview(row.uri)
                },
                onImportClick = { importLauncher.launch(arrayOf("audio/*")) },
                onRecordToggle = viewModel::toggleRecording,
                onPreview = viewModel::togglePreview,
                onDelete = viewModel::delete,
                onDone = onDone,
            ),
        modifier = modifier,
    )
}

@Composable
private fun PickerMessageEffect(
    message: PickerMessage?,
    snackbar: SnackbarHostState,
    onUndo: () -> Unit,
    onDeleteSettled: () -> Unit,
    onConsumed: () -> Unit,
) {
    val text = message?.let { stringResource(it.textRes()) }
    val undo = stringResource(R.string.ringtone_undo)
    LaunchedEffect(message) {
        if (message == null || text == null) return@LaunchedEffect
        onConsumed()
        if (message == PickerMessage.Deleted) {
            val result =
                snackbar.showSnackbar(
                    message = text,
                    actionLabel = undo,
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) onUndo() else onDeleteSettled()
        } else {
            snackbar.showSnackbar(text)
        }
    }
}

/** Which tab a sound lives in. */
private enum class PickerTab(val label: Int) {
    Included(R.string.ringtone_tab_included),
    Device(R.string.ringtone_tab_device),
    Mine(R.string.ringtone_tab_mine),
}

private fun CatalogRingtone.tab(): PickerTab =
    when (kind) {
        RingtoneKind.DEVICE_ALARM -> PickerTab.Device
        RingtoneKind.MUSIC,
        RingtoneKind.RECORD -> PickerTab.Mine
        else -> PickerTab.Included
    }

private fun initialTab(state: PickerState, current: String?): PickerTab {
    if (current == null) return PickerTab.Included
    val row = (state.rows + state.deviceTones).firstOrNull { it.uri == current }
    return row?.tab() ?: PickerTab.Included
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RingtonePickerContent(
    state: PickerState,
    mode: PickerMode,
    selectedUri: String?,
    callbacks: RingtonePickerCallbacks,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var picked by rememberSaveable(selectedUri) { mutableStateOf(selectedUri) }
    val current = if (mode == PickerMode.Default) state.defaultUri else picked
    var tab by rememberSaveable { mutableIntStateOf(initialTab(state, current).ordinal) }

    val choose: (uri: String, title: String, row: CatalogRingtone?) -> Unit = { uri, title, row ->
        when {
            uri == current && row != null -> callbacks.onPreview(uri)
            mode == PickerMode.Default && row != null -> callbacks.onSetDefault(row)
            else -> {
                picked = uri
                callbacks.onSelect(uri, title)
            }
        }
    }

    AlarmeScaffold(
        title =
            stringResource(
                when (mode) {
                    PickerMode.Alarm -> R.string.ringtone_title_alarm
                    PickerMode.Default -> R.string.ringtone_title_default
                    PickerMode.Backup -> R.string.ringtone_title_backup
                },
            ),
        modifier = modifier.testTag("ringtone:title"),
        onBack = callbacks.onDone,
        backContentDescription = stringResource(R.string.ringtone_done),
        backTag = "ringtone:done",
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
        ) {
            PrimaryTabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                PickerTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry.ordinal,
                        onClick = { tab = entry.ordinal },
                        text = {
                            Text(
                                stringResource(entry.label),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.testTag("ringtone:tab:${entry.ordinal}"),
                    )
                }
            }
            val enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
            val exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
            AnimatedContent(
                targetState = PickerTab.entries[tab],
                transitionSpec = { enter togetherWith exit },
                label = "pickerTab",
                modifier = Modifier.weight(1f),
            ) { shown ->
                val bottomOnly = remember(innerPadding) { BottomPadding(innerPadding) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("ringtone:list"),
                    contentPadding =
                        rememberListPadding(
                            bottomOnly,
                            horizontal = Spacing.screen,
                            bottom = Spacing.xxl,
                        ),
                ) {
                    val rowState = RowState(mode, current, state.previewing)
                    when (shown) {
                        PickerTab.Included -> includedTab(state, mode, rowState, choose)
                        PickerTab.Device -> soundRows(state.deviceTones, rowState, choose)
                        PickerTab.Mine -> mineTab(state, rowState, choose, callbacks)
                    }
                }
            }
        }
    }
}

/** What every row needs to know to draw its selection and preview state. */
private class RowState(val mode: PickerMode, val current: String?, val previewing: String?)

private fun LazyListScope.includedTab(
    state: PickerState,
    mode: PickerMode,
    rowState: RowState,
    choose: (String, String, CatalogRingtone?) -> Unit,
) {
    if (mode == PickerMode.Alarm) {
        item(key = "quick-header") {
            SectionHeader(stringResource(R.string.ringtone_section_quick))
        }
        item(key = "silent") {
            PseudoRow(
                title = stringResource(R.string.ringtone_silent),
                icon = Icons.AutoMirrored.Outlined.VolumeOff,
                selected = rowState.current == RingtoneUris.SILENT,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                onClick = { choose(RingtoneUris.SILENT, "", null) },
                modifier = Modifier.testTag("ringtone:silent"),
            )
        }
        item(key = "random") {
            PseudoRow(
                title = stringResource(R.string.ringtone_random),
                supporting = stringResource(R.string.ringtone_random_body),
                icon = Icons.Outlined.Shuffle,
                selected = rowState.current == RingtoneUris.RANDOM,
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                onClick = { choose(RingtoneUris.RANDOM, "", null) },
                modifier = Modifier.testTag("ringtone:random"),
            )
        }
    }
    val bundled = state.rows.filter { it.kind == RingtoneKind.BUNDLED }
    if (bundled.isNotEmpty()) {
        item(key = "bundled-header") {
            SectionHeader(stringResource(R.string.ringtone_section_bundled))
        }
        soundRows(bundled, rowState, choose)
    }
}

private fun LazyListScope.mineTab(
    state: PickerState,
    rowState: RowState,
    choose: (String, String, CatalogRingtone?) -> Unit,
    callbacks: RingtonePickerCallbacks,
) {
    val mine = state.rows.filter { it.tab() == PickerTab.Mine }
    if (mine.isEmpty()) {
        item(key = "mine-empty") {
            EmptyState(
                icon = Icons.Outlined.LibraryMusic,
                title = stringResource(R.string.ringtone_mine_empty_title),
                body = stringResource(R.string.ringtone_mine_empty_body),
                modifier = Modifier.animateItem(),
            )
        }
    } else {
        item(key = "mine-header") { SectionHeader(stringResource(R.string.ringtone_section_mine)) }
        soundRows(mine, rowState, choose, onDelete = callbacks.onDelete)
    }
    item(key = "mine-actions") {
        AddSoundButtons(
            recording = state.recording,
            onImport = callbacks.onImportClick,
            onRecord = callbacks.onRecordToggle,
            modifier = Modifier.animateItem().padding(top = Spacing.xl),
        )
    }
}

private fun LazyListScope.soundRows(
    rows: List<CatalogRingtone>,
    rowState: RowState,
    choose: (String, String, CatalogRingtone?) -> Unit,
    onDelete: ((CatalogRingtone) -> Unit)? = null,
) {
    itemsIndexed(rows, key = { _, row -> "row-${row.id}" }) { index, row ->
        SoundRow(
            row = row,
            icon =
                when (row.kind) {
                    RingtoneKind.RECORD -> Icons.Outlined.Mic
                    RingtoneKind.DEVICE_ALARM -> Icons.Outlined.Alarm
                    else -> Icons.Outlined.MusicNote
                },
            selected = rowState.current == row.uri,
            playing = rowState.previewing == row.uri,
            shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size),
            onClick = { choose(row.uri, row.title, row) },
            onDelete = onDelete?.let { delete -> { delete(row) } },
            modifier = Modifier.animateItem(),
        )
    }
}

private fun PickerMessage.textRes(): Int =
    when (this) {
        PickerMessage.Imported -> R.string.ringtone_msg_imported
        PickerMessage.RejectedTooLarge -> R.string.ringtone_msg_too_large
        PickerMessage.RejectedTooLong -> R.string.ringtone_msg_too_long
        PickerMessage.RejectedUndecodable -> R.string.ringtone_msg_undecodable
        PickerMessage.ImportFailed -> R.string.ringtone_msg_import_failed
        PickerMessage.Recorded -> R.string.ringtone_msg_recorded
        PickerMessage.RecordingTooShort -> R.string.ringtone_msg_recording_too_short
        PickerMessage.RecordingFailed -> R.string.ringtone_msg_recording_failed
        PickerMessage.Deleted -> R.string.ringtone_msg_deleted
    }
