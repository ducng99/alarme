package dev.tomng.alarme.feature.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.NavRow
import dev.tomng.alarme.core.designsystem.component.SegmentedGroup
import dev.tomng.alarme.core.designsystem.component.SwitchRow
import dev.tomng.alarme.core.designsystem.component.ValueSheet
import dev.tomng.alarme.core.model.alarm.SnoozeOption
import dev.tomng.alarme.core.model.mission.Mission
import kotlin.math.roundToInt
import kotlinx.datetime.DayOfWeek

/** Sound, volume, vibration, crescendo, backup sound and the time announcement. */
@Composable
internal fun SoundGroup(
    draft: AlarmDraft,
    soundTitle: String?,
    backupSoundTitle: String?,
    callbacks: AlarmEditorCallbacks,
) {
    SegmentedGroup {
        row { shapes ->
            NavRow(
                shapes = shapes,
                title = stringResource(R.string.editor_sound),
                supporting = soundTitle ?: stringResource(R.string.editor_sound_default),
                icon = Icons.Outlined.MusicNote,
                onClick = { callbacks.onOpenSound(draft.ringtoneUri) },
                modifier = Modifier.testTag("editor_sound"),
            )
        }
        row { shapes -> VolumeRow(shapes, draft.volumeRatio, callbacks.onVolume) }
        row { shapes ->
            SwitchRow(
                shapes = shapes,
                title = stringResource(R.string.editor_vibrate),
                checked = draft.vibrate,
                icon = Icons.Outlined.Vibration,
                onCheckedChange = callbacks.onVibrate,
                modifier = Modifier.testTag("editor_vibrate"),
            )
        }
        row { shapes ->
            SwitchRow(
                shapes = shapes,
                title = stringResource(R.string.editor_crescendo),
                checked = draft.crescendoEnabled,
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                onCheckedChange = callbacks.onCrescendo,
                modifier = Modifier.testTag("editor_crescendo"),
            )
        }
        if (draft.crescendoEnabled) {
            row { shapes ->
                ValueRow(
                    shapes = shapes,
                    title = stringResource(R.string.editor_crescendo_length),
                    value = draft.crescendoSeconds,
                    valueText =
                        stringResource(R.string.editor_value_seconds, draft.crescendoSeconds),
                    unit = stringResource(R.string.editor_unit_seconds),
                    options = CRESCENDO_OPTIONS,
                    tag = "editor_crescendo_seconds",
                    onChange = callbacks.onCrescendoSeconds,
                )
            }
        }
        row { shapes ->
            SwitchRow(
                shapes = shapes,
                title = stringResource(R.string.editor_backup_sound),
                supporting = stringResource(R.string.editor_backup_sound_body),
                checked = draft.backupSoundEnabled,
                icon = Icons.Outlined.SettingsBackupRestore,
                onCheckedChange = callbacks.onBackupSound,
                modifier = Modifier.testTag("editor_backup_sound"),
            )
        }
        if (draft.backupSoundEnabled) {
            row { shapes ->
                NavRow(
                    shapes = shapes,
                    title = stringResource(R.string.editor_backup_sound_pick),
                    supporting =
                        backupSoundTitle ?: stringResource(R.string.editor_backup_sound_system),
                    icon = Icons.Outlined.MusicNote,
                    onClick = { callbacks.onOpenBackupSound(draft.backupSoundUri) },
                    modifier = Modifier.testTag("editor_backup_sound_pick"),
                )
            }
        }
        row { shapes ->
            SwitchRow(
                shapes = shapes,
                title = stringResource(R.string.editor_time_announcement),
                checked = draft.timeAnnouncementEnabled,
                icon = Icons.Outlined.Campaign,
                onCheckedChange = callbacks.onTimeAnnouncement,
                modifier = Modifier.testTag("editor_time_announcement"),
            )
        }
    }
}

/** Snooze and its length/limit, then the wake-up check. */
@Composable
internal fun WakeUpGroup(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    SegmentedGroup {
        row { shapes ->
            SwitchRow(
                shapes = shapes,
                title = stringResource(R.string.editor_snooze),
                checked = draft.snoozeEnabled,
                icon = Icons.Outlined.Snooze,
                onCheckedChange = callbacks.onSnoozeEnabled,
                modifier = Modifier.testTag("editor_snooze"),
            )
        }
        if (draft.snoozeEnabled) {
            row { shapes ->
                ValueRow(
                    shapes = shapes,
                    title = stringResource(R.string.editor_snooze_length),
                    value = draft.snoozeMinutes,
                    valueText = stringResource(R.string.editor_value_minutes, draft.snoozeMinutes),
                    unit = stringResource(R.string.editor_unit_minutes),
                    options = (AlarmEditorViewModel.MIN_SNOOZE..SNOOZE_PICKER_MAX).toList(),
                    tag = "editor_snooze_minutes",
                    onChange = callbacks.onSnoozeMinutes,
                )
            }
            row { shapes ->
                ValueRow(
                    shapes = shapes,
                    title = stringResource(R.string.editor_snooze_limit),
                    value = draft.snoozeLimit,
                    valueText =
                        if (draft.snoozeLimit == SnoozeOption.UNLIMITED) {
                            stringResource(R.string.editor_value_no_snooze)
                        } else {
                            stringResource(R.string.editor_value_times, draft.snoozeLimit)
                        },
                    unit = stringResource(R.string.editor_unit_times),
                    options = (0..AlarmEditorViewModel.MAX_SNOOZE_LIMIT).toList(),
                    tag = "editor_snooze_limit",
                    onChange = callbacks.onSnoozeLimit,
                )
            }
        }
        row { shapes ->
            SwitchRow(
                shapes = shapes,
                title = stringResource(R.string.editor_wake_up_check),
                supporting = stringResource(R.string.editor_wake_up_check_body),
                checked = draft.wakeUpCheckMinutes > 0,
                icon = Icons.Outlined.Alarm,
                onCheckedChange = { enabled ->
                    callbacks.onWakeUpCheckMinutes(if (enabled) DEFAULT_WAKE_UP_CHECK else 0)
                },
                modifier = Modifier.testTag("editor_wake_up_check"),
            )
        }
        if (draft.wakeUpCheckMinutes > 0) {
            row { shapes ->
                ValueRow(
                    shapes = shapes,
                    title = stringResource(R.string.editor_wake_up_check_after),
                    value = draft.wakeUpCheckMinutes,
                    valueText =
                        stringResource(R.string.editor_value_minutes, draft.wakeUpCheckMinutes),
                    unit = stringResource(R.string.editor_unit_minutes),
                    options = (1..WAKE_UP_CHECK_PICKER_MAX).toList(),
                    tag = "editor_wake_up_check_minutes",
                    onChange = callbacks.onWakeUpCheckMinutes,
                )
            }
        }
    }
}

@Composable
internal fun MemoGroup(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    SegmentedGroup {
        row { shapes ->
            TextRow(
                shapes = shapes,
                value = draft.memo,
                onValueChange = callbacks.onMemo,
                placeholder = stringResource(R.string.editor_memo_hint),
                icon = Icons.AutoMirrored.Outlined.Notes,
                singleLine = false,
                tag = "editor_memo",
            )
        }
        row { shapes ->
            SwitchRow(
                shapes = shapes,
                title = stringResource(R.string.editor_tts),
                checked = draft.ttsEnabled,
                icon = Icons.Outlined.RecordVoiceOver,
                onCheckedChange = callbacks.onTts,
                modifier = Modifier.testTag("editor_tts"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VolumeRow(shapes: ListItemShapes, value: Float, onValueChange: (Float) -> Unit) {
    SegmentedListItem(
        shapes = shapes,
        leadingContent = { Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null) },
        trailingContent = {
            Text(
                text = "${(value * PERCENT).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 40.dp),
            )
        },
        supportingContent = {
            Slider(
                value = value,
                onValueChange = { onValueChange((it * VOLUME_STOPS).roundToInt() / VOLUME_STOPS) },
                modifier = Modifier.fillMaxWidth().testTag("editor_volume"),
            )
        },
    ) {
        Text(stringResource(R.string.editor_volume_label))
    }
}

/** A row showing a number; tapping it opens a wheel to pick a new one. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ValueRow(
    shapes: ListItemShapes,
    title: String,
    value: Int,
    valueText: String,
    unit: String,
    options: List<Int>,
    tag: String,
    onChange: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    if (open) {
        ValueSheet(
            title = title,
            values = options,
            initial = value,
            unit = { unit },
            confirmLabel = stringResource(R.string.config_done),
            cancelLabel = stringResource(R.string.editor_cancel),
            onDismiss = { open = false },
            onConfirm = {
                open = false
                onChange(it)
            },
            tag = "$tag:sheet",
        )
    }
    SegmentedListItem(
        onClick = { open = true },
        shapes = shapes,
        modifier = Modifier.testTag(tag),
        leadingContent = { Box40() },
        trailingContent = {
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("$tag:value"),
            )
        },
    ) {
        Text(title)
    }
}

/** Keeps sub-rows aligned with the icon column of the rows above them. */
@Composable
private fun Box40() {
    androidx.compose.foundation.layout.Spacer(Modifier.size(40.dp))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TextRow(
    shapes: ListItemShapes,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector?,
    tag: String,
    singleLine: Boolean = true,
    leading: (@Composable () -> Unit)? = icon?.let { { Icon(it, contentDescription = null) } },
) {
    SegmentedListItem(shapes = shapes, leadingContent = leading) {
        androidx.compose.material3.TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else MEMO_LINES,
            placeholder = { Text(placeholder) },
            colors =
                androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
    }
}

/** Minus, the animated value, plus; used by the mission configuration. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Stepper(
    value: Int,
    tag: String,
    onChange: (Int) -> Unit,
    step: Int,
    min: Int,
    max: Int,
) {
    val decreaseLabel = stringResource(R.string.editor_decrease)
    val increaseLabel = stringResource(R.string.editor_increase)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            enabled = value > min,
            shapes = IconButtonDefaults.shapes(),
            modifier =
                Modifier.testTag("$tag:minus").semantics { contentDescription = decreaseLabel },
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = null)
        }
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (up) -it else it } + fadeOut())
            },
            label = "stepper",
        ) { shown ->
            Text(
                text = shown.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("$tag:value").widthIn(min = 48.dp),
            )
        }
        FilledTonalIconButton(
            onClick = { onChange((value + step).coerceAtMost(max)) },
            enabled = value < max,
            shapes = IconButtonDefaults.shapes(),
            modifier =
                Modifier.testTag("$tag:plus").semantics { contentDescription = increaseLabel },
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
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
    val onDays: (Set<DayOfWeek>) -> Unit = {},
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
    val onBackupSoundUri: (String?) -> Unit = {},
    val onTimeAnnouncement: (Boolean) -> Unit = {},
    val onAddMission: (Mission) -> Unit = {},
    val onRemoveMission: (Int) -> Unit = {},
    val onRestoreMission: () -> Unit = {},
    val onMoveMission: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onReplaceMission: (Int, Mission) -> Unit = { _, _ -> },
    val onOpenSound: (current: String?) -> Unit = {},
    val onOpenBackupSound: (current: String?) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onNotArmedShown: (fix: Boolean) -> Unit = {},
)

private const val DEFAULT_WAKE_UP_CHECK = 5
private const val SNOOZE_PICKER_MAX = 60
private const val WAKE_UP_CHECK_PICKER_MAX = 60
private const val PERCENT = 100
private const val VOLUME_STOPS = 20f
private const val MEMO_LINES = 4
private val CRESCENDO_OPTIONS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600)
