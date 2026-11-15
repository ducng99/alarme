package dev.tomng.alarme.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomng.alarme.core.model.alarm.Alarm

/** Hosts the editor for [alarmId] and reports when the user is done with it. */
@Composable
fun AlarmEditorRoute(
    alarmId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlarmEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val soundTitle by viewModel.soundTitle.collectAsStateWithLifecycle()

    LaunchedEffect(alarmId) { viewModel.load(alarmId) }
    LaunchedEffect(state.finished, state.notArmed) {
        if (state.finished != null && !state.notArmed) onDone()
    }

    AlarmEditorScreen(
        state = state,
        soundTitle = soundTitle,
        onBack = onDone,
        callbacks =
            AlarmEditorCallbacks(
                onTime = viewModel::setTime,
                onName = viewModel::setName,
                onEmoji = viewModel::setEmoji,
                onToggleDay = viewModel::toggleDay,
                onEveryDay = viewModel::setEveryDay,
                onOneShot = viewModel::setOneShot,
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
                onTimeAnnouncement = viewModel::setTimeAnnouncementEnabled,
                onAddMission = viewModel::addMission,
                onRemoveMission = viewModel::removeMission,
                onReplaceMission = viewModel::replaceMission,
                onSave = viewModel::save,
                onDelete = viewModel::delete,
                onNotArmedShown = viewModel::notArmedShown,
            ),
        modifier = modifier,
    )
}

/** Convenience for hosts that only know "new alarm". */
val NewAlarmId: Long = Alarm.NEW_ID
