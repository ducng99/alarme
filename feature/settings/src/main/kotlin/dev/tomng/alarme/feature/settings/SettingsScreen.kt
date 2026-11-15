package dev.tomng.alarme.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.GroupCard
import dev.tomng.alarme.core.designsystem.component.GroupDivider
import dev.tomng.alarme.core.designsystem.component.LeadingIcon
import dev.tomng.alarme.core.designsystem.component.ScreenHeader
import dev.tomng.alarme.core.designsystem.component.SectionLabel
import dev.tomng.alarme.core.designsystem.component.SettingRow
import dev.tomng.alarme.feature.settings.permissions.PermissionCenterState
import dev.tomng.alarme.feature.settings.permissions.PermissionItem
import dev.tomng.alarme.feature.settings.permissions.PermissionRow
import dev.tomng.alarme.feature.settings.reliability.ReliabilityState

@Composable
fun SettingsScreen(
    permissions: PermissionCenterState,
    reliability: ReliabilityState,
    callbacks: SettingsCallbacks,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .testTag("settings_screen"),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.settings_title),
                    navigation = {
                        IconButton(
                            onClick = callbacks.onBack,
                            modifier = Modifier.testTag("settings_done"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                            )
                        }
                    },
                )
            }
            item { PermissionBanner(permissions) }
            item { SectionLabel(stringResource(R.string.settings_permissions_title)) }
            item {
                GroupCard {
                    permissions.rows.forEachIndexed { index, row ->
                        if (index > 0) GroupDivider()
                        PermissionRowItem(
                            row = row,
                            onOpen = { callbacks.onOpenPermission(row.item) },
                        )
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.settings_sound_title)) }
            item {
                GroupCard {
                    SettingRow(
                        title = stringResource(R.string.settings_default_sound_title),
                        supporting = stringResource(R.string.settings_default_sound_body),
                        leading = { LeadingIcon(Icons.Outlined.MusicNote) },
                        trailing = { Chevron() },
                        onClick = callbacks.onOpenDefaultSound,
                        modifier = Modifier.testTag("settings_default_sound"),
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.settings_reliability_title)) }
            item { ReliabilityGroup(reliability, callbacks) }
            item { SectionLabel(stringResource(R.string.settings_troubleshooting_title)) }
            item {
                GroupCard {
                    SettingRow(
                        title = stringResource(R.string.settings_diagnostics_title),
                        supporting = stringResource(R.string.settings_diagnostics_body),
                        leading = { LeadingIcon(Icons.Outlined.BugReport) },
                        trailing = { Chevron() },
                        onClick = callbacks.onOpenDiagnostics,
                        modifier = Modifier.testTag("open_diagnostics"),
                    )
                    GroupDivider()
                    SettingRow(
                        title = stringResource(R.string.settings_optimize_title),
                        supporting = stringResource(R.string.settings_optimize_body),
                        leading = { LeadingIcon(Icons.Outlined.BatterySaver) },
                        trailing = { Chevron() },
                        onClick = callbacks.onOpenOptimize,
                        modifier = Modifier.testTag("open_optimize"),
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.settings_backup_title)) }
            item {
                GroupCard {
                    SettingRow(
                        title = stringResource(R.string.settings_backup_row),
                        supporting = stringResource(R.string.settings_backup_body),
                        leading = { LeadingIcon(Icons.Outlined.Archive) },
                        trailing = { Chevron() },
                        onClick = callbacks.onOpenBackup,
                        modifier = Modifier.testTag("open_backup"),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(permissions: PermissionCenterState) {
    val granted = permissions.allGranted
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("permissions_banner"),
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (granted) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingIcon(
                icon = if (granted) Icons.Outlined.Notifications else Icons.Outlined.Alarm,
            )
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    text =
                        if (granted) {
                            stringResource(R.string.settings_permissions_all_granted)
                        } else {
                            stringResource(
                                R.string.settings_permissions_missing,
                                permissions.missingCount,
                            )
                        },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (!granted) {
                    Text(
                        text = stringResource(R.string.settings_permissions_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRowItem(row: PermissionRow, onOpen: () -> Unit) {
    SettingRow(
        title = stringResource(row.item.titleRes()),
        supporting = stringResource(row.item.bodyRes()),
        leading = { LeadingIcon(row.item.icon()) },
        onClick = onOpen,
        trailing = {
            StatePill(
                text =
                    stringResource(
                        if (row.granted) R.string.settings_permission_granted
                        else R.string.settings_permission_missing,
                    ),
                positive = row.granted,
            )
        },
        modifier = Modifier.testTag("permission_${row.item.name}"),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    supporting: String,
    leading: @Composable () -> Unit,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        supporting = supporting,
        leading = leading,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(tag),
            )
        },
    )
}

@Composable
private fun StatePill(text: String, positive: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color =
            if (positive) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        contentColor =
            if (positive) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun PermissionItem.icon(): ImageVector =
    when (this) {
        PermissionItem.NOTIFICATIONS -> Icons.Outlined.Notifications
        PermissionItem.EXACT_ALARM -> Icons.Outlined.Alarm
        PermissionItem.FULL_SCREEN_INTENT -> Icons.Outlined.OpenInFull
        PermissionItem.OVERLAY -> Icons.Outlined.Layers
        PermissionItem.DND -> Icons.Outlined.DoNotDisturbOn
        PermissionItem.BATTERY -> Icons.Outlined.BatterySaver
        PermissionItem.CAMERA -> Icons.Outlined.PhotoCamera
        PermissionItem.MICROPHONE -> Icons.Outlined.Mic
    }

internal fun PermissionItem.titleRes(): Int =
    when (this) {
        PermissionItem.NOTIFICATIONS -> R.string.settings_permission_notifications
        PermissionItem.EXACT_ALARM -> R.string.settings_permission_exact_alarm
        PermissionItem.FULL_SCREEN_INTENT -> R.string.settings_permission_full_screen_intent
        PermissionItem.OVERLAY -> R.string.settings_permission_overlay
        PermissionItem.DND -> R.string.settings_permission_dnd
        PermissionItem.BATTERY -> R.string.settings_permission_battery
        PermissionItem.CAMERA -> R.string.settings_permission_camera
        PermissionItem.MICROPHONE -> R.string.settings_permission_microphone
    }

internal fun PermissionItem.bodyRes(): Int =
    when (this) {
        PermissionItem.NOTIFICATIONS -> R.string.settings_permission_notifications_body
        PermissionItem.EXACT_ALARM -> R.string.settings_permission_exact_alarm_body
        PermissionItem.FULL_SCREEN_INTENT -> R.string.settings_permission_full_screen_intent_body
        PermissionItem.OVERLAY -> R.string.settings_permission_overlay_body
        PermissionItem.DND -> R.string.settings_permission_dnd_body
        PermissionItem.BATTERY -> R.string.settings_permission_battery_body
        PermissionItem.CAMERA -> R.string.settings_permission_camera_body
        PermissionItem.MICROPHONE -> R.string.settings_permission_microphone_body
    }

@Composable
private fun ReliabilityGroup(reliability: ReliabilityState, callbacks: SettingsCallbacks) {
    GroupCard {
        ToggleRow(
            title = stringResource(R.string.settings_prevent_power_off),
            supporting =
                when {
                    reliability.preventPowerOff && !reliability.preventPowerOffArmed ->
                        stringResource(R.string.settings_prevent_power_off_needs)
                    reliability.blockedCount > 0 ->
                        stringResource(
                            R.string.settings_prevent_power_off_blocked,
                            reliability.blockedCount,
                        )
                    else -> stringResource(R.string.settings_prevent_power_off_body)
                },
            leading = { LeadingIcon(Icons.Outlined.PowerSettingsNew) },
            checked = reliability.preventPowerOff,
            tag = "toggle_prevent_power_off",
            onCheckedChange = callbacks.onTogglePreventPowerOff,
        )
        GroupDivider()
        ToggleRow(
            title = stringResource(R.string.settings_prevent_uninstall),
            supporting = stringResource(R.string.settings_prevent_uninstall_body),
            leading = { LeadingIcon(Icons.Outlined.Block) },
            checked = reliability.preventUninstall,
            tag = "toggle_prevent_uninstall",
            onCheckedChange = callbacks.onTogglePreventUninstall,
        )
    }
}
