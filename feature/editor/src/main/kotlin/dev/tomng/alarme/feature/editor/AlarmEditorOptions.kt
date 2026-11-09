package dev.tomng.alarme.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Vibrate, volume, crescendo, backup sound and the time announcement. */
@Composable
internal fun RingOptions(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleRow(
            title = stringResource(R.string.editor_vibrate),
            checked = draft.vibrate,
            tag = "editor_vibrate",
            onCheckedChange = callbacks.onVibrate,
        )
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                text =
                    stringResource(
                        R.string.editor_volume,
                        (draft.volumeRatio * 100).roundToInt(),
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = draft.volumeRatio,
                onValueChange = callbacks.onVolume,
                modifier = Modifier.testTag("editor_volume"),
            )
        }
        ToggleRow(
            title = stringResource(R.string.editor_crescendo),
            checked = draft.crescendoEnabled,
            tag = "editor_crescendo",
            onCheckedChange = callbacks.onCrescendo,
        )
        if (draft.crescendoEnabled) {
            StepperRow(
                label = stringResource(R.string.editor_crescendo_seconds),
                value = draft.crescendoSeconds,
                tag = "editor_crescendo_seconds",
                onChange = callbacks.onCrescendoSeconds,
                step = 5,
                min = AlarmEditorViewModel.MIN_CRESCENDO,
                max = AlarmEditorViewModel.MAX_CRESCENDO,
            )
        }
        ToggleRow(
            title = stringResource(R.string.editor_backup_sound),
            body = stringResource(R.string.editor_backup_sound_body),
            checked = draft.backupSoundEnabled,
            tag = "editor_backup_sound",
            onCheckedChange = callbacks.onBackupSound,
        )
        ToggleRow(
            title = stringResource(R.string.editor_time_announcement),
            checked = draft.timeAnnouncementEnabled,
            tag = "editor_time_announcement",
            onCheckedChange = callbacks.onTimeAnnouncement,
        )
    }
}

@Composable
internal fun SnoozeSection(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleRow(
            title = stringResource(R.string.editor_snooze),
            checked = draft.snoozeEnabled,
            tag = "editor_snooze",
            onCheckedChange = callbacks.onSnoozeEnabled,
        )
        if (draft.snoozeEnabled) {
            StepperRow(
                label = stringResource(R.string.editor_snooze_minutes),
                value = draft.snoozeMinutes,
                tag = "editor_snooze_minutes",
                onChange = callbacks.onSnoozeMinutes,
                step = 1,
                min = AlarmEditorViewModel.MIN_SNOOZE,
                max = AlarmEditorViewModel.MAX_SNOOZE,
            )
            StepperRow(
                label = stringResource(R.string.editor_snooze_limit),
                value = draft.snoozeLimit,
                tag = "editor_snooze_limit",
                onChange = callbacks.onSnoozeLimit,
                step = 1,
                min = 0,
                max = AlarmEditorViewModel.MAX_SNOOZE_LIMIT,
            )
        }
    }
}

@Composable
internal fun WakeUpCheckSection(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleRow(
            title = stringResource(R.string.editor_wake_up_check),
            body = stringResource(R.string.editor_wake_up_check_body),
            checked = draft.wakeUpCheckMinutes > 0,
            tag = "editor_wake_up_check",
            onCheckedChange = { enabled ->
                callbacks.onWakeUpCheckMinutes(if (enabled) DEFAULT_WAKE_UP_CHECK else 0)
            },
        )
        if (draft.wakeUpCheckMinutes > 0) {
            StepperRow(
                label = stringResource(R.string.editor_wake_up_check_minutes),
                value = draft.wakeUpCheckMinutes,
                tag = "editor_wake_up_check_minutes",
                onChange = callbacks.onWakeUpCheckMinutes,
                step = 1,
                min = 1,
                max = AlarmEditorViewModel.MAX_WAKE_UP_CHECK,
            )
        }
    }
}

@Composable
internal fun MemoSection(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = draft.memo,
            onValueChange = callbacks.onMemo,
            label = { Text(stringResource(R.string.editor_memo)) },
            placeholder = { Text(stringResource(R.string.editor_memo_hint)) },
            modifier = Modifier.fillMaxWidth().testTag("editor_memo"),
        )
        ToggleRow(
            title = stringResource(R.string.editor_tts),
            checked = draft.ttsEnabled,
            tag = "editor_tts",
            onCheckedChange = callbacks.onTts,
        )
    }
}

@Composable
internal fun ExtrasSection(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleRow(
            title = stringResource(R.string.editor_enabled),
            checked = draft.enabled,
            tag = "editor_enabled",
            onCheckedChange = callbacks.onEnabled,
        )
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
    body: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) {
                Text(text = body, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
internal fun StepperRow(
    label: String,
    value: Int,
    tag: String,
    onChange: (Int) -> Unit,
    step: Int,
    min: Int,
    max: Int,
) {
    val decreaseLabel = stringResource(R.string.editor_decrease)
    val increaseLabel = stringResource(R.string.editor_increase)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            enabled = value > min,
            modifier =
                Modifier.testTag("$tag:minus").semantics { contentDescription = decreaseLabel },
        ) {
            Text("−")
        }
        Text(text = value.toString(), modifier = Modifier.testTag("$tag:value"))
        TextButton(
            onClick = { onChange((value + step).coerceAtMost(max)) },
            enabled = value < max,
            modifier =
                Modifier.testTag("$tag:plus").semantics { contentDescription = increaseLabel },
        ) {
            Text("+")
        }
    }
}

private const val DEFAULT_WAKE_UP_CHECK = 5
