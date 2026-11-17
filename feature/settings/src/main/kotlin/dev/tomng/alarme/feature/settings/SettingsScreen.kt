package dev.tomng.alarme.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.tomng.alarme.core.designsystem.component.AlarmeScaffold
import dev.tomng.alarme.core.designsystem.component.BannerTone
import dev.tomng.alarme.core.designsystem.component.InlineBanner
import dev.tomng.alarme.core.designsystem.component.NavRow
import dev.tomng.alarme.core.designsystem.component.SegmentedGroup
import dev.tomng.alarme.core.designsystem.component.SwitchRow
import dev.tomng.alarme.core.designsystem.component.rememberListPadding
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.feature.settings.permissions.PermissionCenterState
import dev.tomng.alarme.feature.settings.permissions.PermissionItem
import dev.tomng.alarme.feature.settings.reliability.ReliabilityState
import dev.tomng.alarme.feature.settings.sound.SoundSettingsState

@Composable
fun SettingsScreen(
    permissions: PermissionCenterState,
    reliability: ReliabilityState,
    callbacks: SettingsCallbacks,
    modifier: Modifier = Modifier,
    sound: SoundSettingsState = SoundSettingsState(),
) {
    AlarmeScaffold(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        onBack = callbacks.onBack,
        backContentDescription = stringResource(R.string.settings_back),
        backTag = "settings_done",
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings_screen"),
            contentPadding =
                rememberListPadding(
                    innerPadding,
                    horizontal = Spacing.screen,
                    bottom = Spacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "status") { StatusHero(permissions, callbacks.onFixPermissions) }
            item(key = "permissions") {
                SectionHeader(R.string.settings_permissions_title)
                SegmentedGroup {
                    row { shapes ->
                        NavRow(
                            shapes = shapes,
                            title = stringResource(R.string.settings_permissions_row),
                            supporting =
                                if (permissions.allGranted) {
                                    stringResource(R.string.settings_permissions_row_ok)
                                } else {
                                    stringResource(
                                        R.string.settings_permissions_row_missing,
                                        permissions.missingCount,
                                    )
                                },
                            icon = Icons.Outlined.Shield,
                            onClick = callbacks.onOpenPermissions,
                            modifier = Modifier.testTag("open_permissions"),
                        )
                    }
                }
            }
            item(key = "sound") {
                SectionHeader(R.string.settings_sound_title)
                SegmentedGroup {
                    row { shapes ->
                        NavRow(
                            shapes = shapes,
                            title = stringResource(R.string.settings_default_sound_title),
                            supporting = stringResource(R.string.settings_default_sound_body),
                            icon = Icons.Outlined.MusicNote,
                            onClick = callbacks.onOpenDefaultSound,
                            modifier = Modifier.testTag("settings_default_sound"),
                        )
                    }
                    row { shapes ->
                        SwitchRow(
                            shapes = shapes,
                            title = stringResource(R.string.settings_mute_during_mission),
                            supporting = stringResource(R.string.settings_mute_during_mission_body),
                            icon = Icons.AutoMirrored.Outlined.VolumeOff,
                            checked = sound.muteDuringMission,
                            onCheckedChange = callbacks.onToggleMuteDuringMission,
                            modifier = Modifier.testTag("toggle_mute_during_mission"),
                        )
                    }
                }
            }
            item(key = "reliability") {
                SectionHeader(R.string.settings_reliability_title)
                ReliabilityGroup(reliability, callbacks)
            }
            item(key = "troubleshooting") {
                SectionHeader(R.string.settings_troubleshooting_title)
                SegmentedGroup {
                    row { shapes ->
                        NavRow(
                            shapes = shapes,
                            title = stringResource(R.string.settings_optimize_title),
                            supporting = stringResource(R.string.settings_optimize_body),
                            icon = Icons.Outlined.Tune,
                            onClick = callbacks.onOpenOptimize,
                            modifier = Modifier.testTag("open_optimize"),
                        )
                    }
                    row { shapes ->
                        NavRow(
                            shapes = shapes,
                            title = stringResource(R.string.settings_diagnostics_title),
                            supporting = stringResource(R.string.settings_diagnostics_body),
                            icon = Icons.Outlined.BugReport,
                            onClick = callbacks.onOpenDiagnostics,
                            modifier = Modifier.testTag("open_diagnostics"),
                        )
                    }
                }
            }
            item(key = "backup") {
                SectionHeader(R.string.settings_backup_title)
                SegmentedGroup {
                    row { shapes ->
                        NavRow(
                            shapes = shapes,
                            title = stringResource(R.string.settings_backup_row),
                            supporting = stringResource(R.string.settings_backup_body),
                            icon = Icons.Outlined.Archive,
                            onClick = callbacks.onOpenBackup,
                            modifier = Modifier.testTag("open_backup"),
                        )
                    }
                }
            }
        }
    }
}

/** The one-glance answer to "will my alarm ring?", with a way to fix it when it won't. */
@Composable
private fun StatusHero(permissions: PermissionCenterState, onFix: () -> Unit) {
    val critical = permissions.criticalMissing
    val tone =
        when {
            critical.isNotEmpty() -> BannerTone.Error
            !permissions.allGranted -> BannerTone.Neutral
            else -> BannerTone.Success
        }
    val criticalNames = critical.map { stringResource(it.titleRes()) }.joinToString(" · ")
    AnimatedContent(
        targetState = tone,
        label = "status",
        modifier = Modifier.padding(top = Spacing.s).testTag("permissions_banner"),
    ) { shown ->
        when (shown) {
            BannerTone.Error ->
                InlineBanner(
                    title = stringResource(R.string.settings_status_critical),
                    body = criticalNames,
                    icon = Icons.Outlined.PriorityHigh,
                    tone = BannerTone.Error,
                    actionLabel = stringResource(R.string.settings_status_fix),
                    onAction = onFix,
                    actionModifier = Modifier.testTag("settings_fix"),
                )
            BannerTone.Neutral ->
                InlineBanner(
                    title =
                        stringResource(R.string.settings_status_optional, permissions.missingCount),
                    body = stringResource(R.string.settings_status_optional_body),
                    icon = Icons.Outlined.Shield,
                )
            else ->
                InlineBanner(
                    title = stringResource(R.string.settings_status_ready),
                    body = stringResource(R.string.settings_status_ready_body),
                    icon = Icons.Outlined.CheckCircle,
                    tone = BannerTone.Success,
                )
        }
    }
}

@Composable
internal fun SectionHeader(textRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Spacing.l, top = Spacing.xl, bottom = Spacing.s),
    )
}

@Composable
private fun ReliabilityGroup(reliability: ReliabilityState, callbacks: SettingsCallbacks) {
    SegmentedGroup {
        row { shapes ->
            SwitchRow(
                shapes = shapes,
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
                icon = Icons.Outlined.PowerSettingsNew,
                checked = reliability.preventPowerOff,
                onCheckedChange = callbacks.onTogglePreventPowerOff,
                modifier = Modifier.testTag("toggle_prevent_power_off"),
            )
        }
        row { shapes ->
            SwitchRow(
                shapes = shapes,
                title = stringResource(R.string.settings_prevent_uninstall),
                supporting = stringResource(R.string.settings_prevent_uninstall_body),
                icon = Icons.Outlined.Block,
                checked = reliability.preventUninstall,
                onCheckedChange = callbacks.onTogglePreventUninstall,
                modifier = Modifier.testTag("toggle_prevent_uninstall"),
            )
        }
    }
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
