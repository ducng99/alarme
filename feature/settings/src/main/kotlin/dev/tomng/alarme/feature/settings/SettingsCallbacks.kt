package dev.tomng.alarme.feature.settings

import dev.tomng.alarme.feature.settings.permissions.PermissionItem

/** Everything the settings hub can ask the host to do. */
data class SettingsCallbacks(
    val onOpenPermission: (PermissionItem) -> Unit = {},
    val onTogglePreventPowerOff: (Boolean) -> Unit = {},
    val onTogglePreventUninstall: (Boolean) -> Unit = {},
    val onOpenDiagnostics: () -> Unit = {},
    val onOpenOptimize: () -> Unit = {},
    val onOpenBackup: () -> Unit = {},
    val onBack: () -> Unit = {},
)
