package dev.tomng.alarme.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomng.alarme.core.model.alarm.Alarm

/** A sound the picker returned for this editor; [backup] says which slot it fills. */
data class PickedSound(val backup: Boolean, val uri: String?)

/** How the editor opens the sound picker and receives its answer from the host. */
class EditorSoundRequests(
    val picked: PickedSound? = null,
    val onConsumed: (backup: Boolean) -> Unit = {},
    val onPickSound: (current: String?) -> Unit = {},
    val onPickBackupSound: (current: String?) -> Unit = {},
)

/** Hosts the editor for [alarmId] and reports when the user is done with it. */
@Composable
fun AlarmEditorRoute(
    alarmId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    sounds: EditorSoundRequests = EditorSoundRequests(),
    onFixPermissions: () -> Unit = {},
    viewModel: AlarmEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val soundTitle by viewModel.soundTitle.collectAsStateWithLifecycle()
    val backupSoundTitle by viewModel.backupSoundTitle.collectAsStateWithLifecycle()

    LaunchedEffect(alarmId) { viewModel.load(alarmId) }
    LaunchedEffect(state.finished, state.notArmed) {
        if (state.finished != null && !state.notArmed) onDone()
    }
    LaunchedEffect(sounds.picked, state.loading) {
        val picked = sounds.picked ?: return@LaunchedEffect
        if (state.loading) return@LaunchedEffect
        if (picked.backup) viewModel.setBackupSound(picked.uri)
        else viewModel.setRingtone(picked.uri)
        sounds.onConsumed(picked.backup)
    }

    AlarmEditorScreen(
        state = state,
        soundTitle = soundTitle,
        backupSoundTitle = backupSoundTitle,
        onBack = onDone,
        callbacks =
            AlarmEditorCallbacks(
                onTime = viewModel::setTime,
                onName = viewModel::setName,
                onEmoji = viewModel::setEmoji,
                onToggleDay = viewModel::toggleDay,
                onEveryDay = viewModel::setEveryDay,
                onOneShot = viewModel::setOneShot,
                onDays = viewModel::setDays,
                onRingtone = viewModel::setRingtone,
                onEnabled = viewModel::setEnabled,
                onVibrate = viewModel::setVibrate,
                onVolume = viewModel::setVolume,
                onCrescendo = viewModel::setCrescendoEnabled,
                onCrescendoSeconds = viewModel::setCrescendoSeconds,
                onSnoozeEnabled = viewModel::setSnoozeEnabled,
                onSnoozeMinutes = viewModel::setSnoozeMinutes,
                onSnoozeLimit = viewModel::setSnoozeLimit,
                onWakeUpCheckMinutes = viewModel::setWakeUpCheckMinutes,
                onMemo = viewModel::setMemo,
                onTts = viewModel::setTtsEnabled,
                onBackupSound = viewModel::setBackupSoundEnabled,
                onBackupSoundUri = viewModel::setBackupSound,
                onTimeAnnouncement = viewModel::setTimeAnnouncementEnabled,
                onAddMission = viewModel::addMission,
                onRemoveMission = viewModel::removeMission,
                onRestoreMission = viewModel::restoreMission,
                onMoveMission = viewModel::moveMission,
                onReplaceMission = viewModel::replaceMission,
                onOpenSound = sounds.onPickSound,
                onOpenBackupSound = sounds.onPickBackupSound,
                onSave = viewModel::save,
                onDelete = viewModel::delete,
                onNotArmedShown = { fix ->
                    viewModel.notArmedShown()
                    if (fix) onFixPermissions()
                },
            ),
        modifier = modifier,
    )
}

/** Convenience for hosts that only know "new alarm". */
val NewAlarmId: Long = Alarm.NEW_ID
