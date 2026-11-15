package dev.tomng.alarme.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.GroupCard
import dev.tomng.alarme.core.designsystem.component.GroupDivider
import dev.tomng.alarme.core.designsystem.component.LeadingIcon
import dev.tomng.alarme.core.designsystem.component.SettingRow
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.MissionType
import kotlin.math.roundToInt
import kotlinx.datetime.DayOfWeek

/** The label, alarm icon, sound and vibration. */
@Composable
internal fun DetailsGroup(
    draft: AlarmDraft,
    soundTitle: String?,
    callbacks: AlarmEditorCallbacks,
    onOpenSound: () -> Unit,
) {
    GroupCard {
        LabelField(
            value = draft.name,
            onValueChange = callbacks.onName,
            leading = { LeadingIcon(Icons.Outlined.Label) },
        )
        EmojiRow(selected = draft.emoji, onSelect = callbacks.onEmoji)
        GroupDivider()
        SettingRow(
            title = stringResource(R.string.editor_sound),
            supporting = soundTitle ?: stringResource(R.string.editor_sound_default),
            leading = { LeadingIcon(Icons.Outlined.MusicNote) },
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = onOpenSound,
            modifier = Modifier.testTag("editor_sound"),
        )
        GroupDivider()
        SettingRow(
            title = stringResource(R.string.editor_vibrate),
            leading = { LeadingIcon(Icons.Outlined.Vibration) },
            onClick = { callbacks.onVibrate(!draft.vibrate) },
            trailing = {
                Switch(
                    checked = draft.vibrate,
                    onCheckedChange = callbacks.onVibrate,
                    modifier = Modifier.testTag("editor_vibrate"),
                )
            },
        )
    }
}

/** Volume, crescendo, backup sound and the time announcement. */
@Composable
internal fun SoundGroup(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    GroupCard {
        VolumeRow(value = draft.volumeRatio, onValueChange = callbacks.onVolume)
        GroupDivider()
        ToggleRow(
            title = stringResource(R.string.editor_crescendo),
            checked = draft.crescendoEnabled,
            tag = "editor_crescendo",
            icon = Icons.Outlined.TrendingUp,
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
        GroupDivider()
        ToggleRow(
            title = stringResource(R.string.editor_backup_sound),
            body = stringResource(R.string.editor_backup_sound_body),
            checked = draft.backupSoundEnabled,
            tag = "editor_backup_sound",
            icon = Icons.Outlined.SettingsBackupRestore,
            onCheckedChange = callbacks.onBackupSound,
        )
        GroupDivider()
        ToggleRow(
            title = stringResource(R.string.editor_time_announcement),
            checked = draft.timeAnnouncementEnabled,
            tag = "editor_time_announcement",
            icon = Icons.Outlined.Campaign,
            onCheckedChange = callbacks.onTimeAnnouncement,
        )
    }
}

/** Snooze length and limit, plus the wake-up check. */
@Composable
internal fun SnoozeGroup(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    GroupCard {
        ToggleRow(
            title = stringResource(R.string.editor_snooze),
            checked = draft.snoozeEnabled,
            tag = "editor_snooze",
            icon = Icons.Outlined.Snooze,
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
    GroupCard {
        ToggleRow(
            title = stringResource(R.string.editor_wake_up_check),
            body = stringResource(R.string.editor_wake_up_check_body),
            checked = draft.wakeUpCheckMinutes > 0,
            tag = "editor_wake_up_check",
            icon = Icons.Outlined.Alarm,
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
    GroupCard {
        LabelField(
            value = draft.memo,
            onValueChange = callbacks.onMemo,
            label = stringResource(R.string.editor_memo_hint),
            tag = "editor_memo",
            leading = { LeadingIcon(Icons.Outlined.Notes) },
        )
        GroupDivider()
        ToggleRow(
            title = stringResource(R.string.editor_tts),
            checked = draft.ttsEnabled,
            tag = "editor_tts",
            icon = Icons.Outlined.RecordVoiceOver,
            onCheckedChange = callbacks.onTts,
        )
    }
}

@Composable
internal fun MissionGroup(
    draft: AlarmDraft,
    callbacks: AlarmEditorCallbacks,
    onEditMission: (Int) -> Unit,
) {
    GroupCard {
        if (draft.missions.isEmpty()) {
            Text(
                text = stringResource(R.string.editor_missions_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            )
        }
        draft.missions.forEachIndexed { index, mission ->
            MissionRow(
                index = index,
                mission = mission,
                onEdit = { onEditMission(index) },
                onRemove = { callbacks.onRemoveMission(index) },
            )
            GroupDivider()
        }
        AddMissionRow(enabled = draft.canAddMission, onAdd = callbacks.onAddMission)
    }
}

@Composable
private fun LabelField(
    value: String,
    onValueChange: (String) -> Unit,
    tag: String = "editor_name",
    label: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = label ?: stringResource(R.string.editor_name),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            modifier = Modifier.weight(1f).testTag(tag),
        )
    }
}

@Composable
private fun EmojiRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EMOJIS.forEach { emoji ->
            Surface(
                shape = CircleShape,
                color =
                    if (emoji == selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(
                    modifier =
                        Modifier.size(34.dp)
                            .clip(CircleShape)
                            .clickable { onSelect(emoji) }
                            .testTag("editor_emoji:$emoji"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun VolumeRow(value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LeadingIcon(Icons.Outlined.VolumeUp)
            Text(
                text = stringResource(R.string.editor_volume, (value * 100).roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp).testTag("editor_volume"),
        )
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
    SettingRow(
        title = stringResource(R.string.editor_mission_slot, index + 1, missionTypeLabel(mission)),
        supporting =
            if (incomplete) stringResource(R.string.editor_mission_incomplete)
            else missionSummary(mission),
        leading = { LeadingIcon(missionIcon(mission)) },
        onClick = onEdit,
        trailing = {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("editor_mission_remove:$index"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.editor_mission_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.testTag("editor_mission:$index"),
    )
}

@Composable
private fun AddMissionRow(enabled: Boolean, onAdd: (MissionType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingRow(
            title = stringResource(R.string.editor_add_mission),
            leading = { LeadingIcon(Icons.Outlined.Add) },
            onClick = { if (enabled) expanded = !expanded },
            enabled = enabled,
            modifier = Modifier.testTag("editor_add_mission"),
        )
        if (expanded) {
            MissionType.entries.forEach { type ->
                SettingRow(
                    title = missionTypeLabel(type),
                    leading = { LeadingIcon(missionIcon(type)) },
                    onClick = {
                        onAdd(type)
                        expanded = false
                    },
                    modifier = Modifier.testTag("editor_mission_type:${type.name}"),
                )
            }
        }
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
    body: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            LeadingIcon(icon)
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        modifier =
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            enabled = value > min,
            colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            modifier =
                Modifier.size(32.dp).testTag("$tag:minus").semantics {
                    contentDescription = decreaseLabel
                },
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier =
                Modifier.testTag("$tag:value").padding(horizontal = 4.dp).widthIn(min = 32.dp),
        )
        FilledTonalIconButton(
            onClick = { onChange((value + step).coerceAtMost(max)) },
            enabled = value < max,
            colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            modifier =
                Modifier.size(32.dp).testTag("$tag:plus").semantics {
                    contentDescription = increaseLabel
                },
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

private const val DEFAULT_WAKE_UP_CHECK = 5

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

private val EMOJIS = listOf("⏰", "🌅", "☕", "🏃", "📚", "💪", "🐱", "🎵")
