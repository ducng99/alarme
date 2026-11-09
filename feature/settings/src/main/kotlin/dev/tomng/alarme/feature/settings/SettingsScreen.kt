package dev.tomng.alarme.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.feature.settings.diagnostics.DiagnosticLabel
import dev.tomng.alarme.feature.settings.diagnostics.DiagnosticLine
import dev.tomng.alarme.feature.settings.optimize.OemGuide
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
    LazyColumn(modifier = modifier.fillMaxSize().testTag("settings_screen")) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = callbacks.onBack,
                    modifier = Modifier.testTag("settings_done"),
                ) {
                    Text(stringResource(R.string.settings_back))
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_permissions_title)) }
        item {
            Text(
                text =
                    if (permissions.allGranted) {
                        stringResource(R.string.settings_permissions_all_granted)
                    } else {
                        stringResource(
                            R.string.settings_permissions_missing,
                            permissions.missingCount,
                        )
                    },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        items(permissions.rows, key = { it.item.name }) { row ->
            PermissionRowItem(row = row, onOpen = { callbacks.onOpenPermission(row.item) })
        }

        item { SectionHeader(stringResource(R.string.settings_reliability_title)) }
        item {
            ToggleRow(
                title = stringResource(R.string.settings_prevent_power_off),
                body =
                    if (reliability.preventPowerOff && !reliability.preventPowerOffArmed) {
                        stringResource(R.string.settings_prevent_power_off_needs)
                    } else {
                        stringResource(R.string.settings_prevent_power_off_body)
                    },
                checked = reliability.preventPowerOff,
                tag = "toggle_prevent_power_off",
                onCheckedChange = callbacks.onTogglePreventPowerOff,
            )
        }
        if (reliability.blockedCount > 0) {
            item {
                Text(
                    text =
                        stringResource(
                            R.string.settings_prevent_power_off_blocked,
                            reliability.blockedCount,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
        item {
            ToggleRow(
                title = stringResource(R.string.settings_prevent_uninstall),
                body = stringResource(R.string.settings_prevent_uninstall_body),
                checked = reliability.preventUninstall,
                tag = "toggle_prevent_uninstall",
                onCheckedChange = callbacks.onTogglePreventUninstall,
            )
        }

        item { SectionHeader(stringResource(R.string.settings_diagnostics_title)) }
        item {
            LinkRow(
                title = stringResource(R.string.settings_diagnostics_title),
                body = stringResource(R.string.settings_diagnostics_body),
                tag = "open_diagnostics",
                onClick = callbacks.onOpenDiagnostics,
            )
        }
        item {
            LinkRow(
                title = stringResource(R.string.settings_optimize_title),
                body = stringResource(R.string.settings_optimize_body),
                tag = "open_optimize",
                onClick = callbacks.onOpenOptimize,
            )
        }

        item { SectionHeader(stringResource(R.string.settings_backup_title)) }
        item {
            LinkRow(
                title = stringResource(R.string.settings_backup_title),
                body = stringResource(R.string.settings_backup_body),
                tag = "open_backup",
                onClick = callbacks.onOpenBackup,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        HorizontalDivider()
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PermissionRowItem(row: PermissionRow, onOpen: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("permission_${row.item.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(row.item.titleRes()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(row.item.bodyRes()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text =
                stringResource(
                    if (row.granted) R.string.settings_permission_granted
                    else R.string.settings_permission_missing,
                ),
            style = MaterialTheme.typography.labelMedium,
            color =
                if (row.granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = body, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun LinkRow(title: String, body: String, tag: String, onClick: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag(tag),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(text = body, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DiagnosticsScreen(
    lines: List<DiagnosticLine>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize().testTag("diagnostics_screen")) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.diagnostics_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onBack, modifier = Modifier.testTag("diagnostics_done")) {
                    Text(stringResource(R.string.settings_back))
                }
            }
        }
        items(lines, key = { it.label.name }) { line ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(line.label.labelRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(text = line.value, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text =
                        stringResource(
                            if (line.healthy) R.string.diagnostics_ok
                            else R.string.diagnostics_attention,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (line.healthy) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
fun OptimizeScreen(
    guide: OemGuide,
    batteryUnrestricted: Boolean,
    dndGranted: Boolean,
    onOpenBattery: () -> Unit,
    onOpenDnd: () -> Unit,
    onOpenAutostart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize().testTag("optimize_screen")) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.optimize_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onBack, modifier = Modifier.testTag("optimize_done")) {
                    Text(stringResource(R.string.settings_back))
                }
            }
        }
        item {
            OptimizeRow(
                title = stringResource(R.string.optimize_battery),
                body = stringResource(R.string.optimize_battery_body),
                granted = batteryUnrestricted,
                tag = "optimize_battery",
                onClick = onOpenBattery,
            )
        }
        item {
            OptimizeRow(
                title = stringResource(R.string.optimize_dnd),
                body = stringResource(R.string.optimize_dnd_body),
                granted = dndGranted,
                tag = "optimize_dnd",
                onClick = onOpenDnd,
            )
        }
        item {
            OptimizeRow(
                title = stringResource(R.string.optimize_autostart),
                body =
                    if (guide == OemGuide.GENERIC) {
                        stringResource(R.string.optimize_generic_body)
                    } else {
                        stringResource(R.string.optimize_autostart_body)
                    },
                granted = null,
                tag = "optimize_autostart",
                onClick = onOpenAutostart,
            )
        }
    }
}

@Composable
private fun OptimizeRow(
    title: String,
    body: String,
    granted: Boolean?,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = body, style = MaterialTheme.typography.bodySmall)
        }
        if (granted != null) {
            Text(
                text =
                    stringResource(
                        if (granted) R.string.optimize_granted else R.string.optimize_missing,
                    ),
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(R.string.optimize_open),
            style = MaterialTheme.typography.labelLarge,
        )
    }
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

internal fun DiagnosticLabel.labelRes(): Int =
    when (this) {
        DiagnosticLabel.BATTERY -> R.string.diagnostics_label_battery
        DiagnosticLabel.CHARGING -> R.string.diagnostics_label_charging
        DiagnosticLabel.POWER_SAVE -> R.string.diagnostics_label_power_save
        DiagnosticLabel.DEVICE_IDLE -> R.string.diagnostics_label_device_idle
        DiagnosticLabel.STANDBY_BUCKET -> R.string.diagnostics_label_standby_bucket
        DiagnosticLabel.BACKGROUND_RESTRICTED -> R.string.diagnostics_label_background_restricted
        DiagnosticLabel.BATTERY_OPTIMIZATION -> R.string.diagnostics_label_battery_optimization
        DiagnosticLabel.EXACT_ALARM -> R.string.diagnostics_label_exact_alarm
        DiagnosticLabel.OVERLAY -> R.string.diagnostics_label_overlay
        DiagnosticLabel.MEMORY -> R.string.diagnostics_label_memory
        DiagnosticLabel.STORAGE -> R.string.diagnostics_label_storage
    }
