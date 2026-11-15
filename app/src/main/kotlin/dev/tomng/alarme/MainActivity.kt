package dev.tomng.alarme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.tomng.alarme.core.designsystem.theme.AlarmeTheme
import dev.tomng.alarme.feature.alarmlist.AlarmListScreen
import dev.tomng.alarme.feature.alarmlist.AlarmListViewModel
import dev.tomng.alarme.feature.backup.BackupScreen
import dev.tomng.alarme.feature.backup.BackupViewModel
import dev.tomng.alarme.feature.editor.AlarmEditorRoute
import dev.tomng.alarme.feature.ringtones.PickerMode
import dev.tomng.alarme.feature.ringtones.RingtonePickerScreen
import dev.tomng.alarme.feature.settings.DiagnosticsScreen
import dev.tomng.alarme.feature.settings.OptimizeScreen
import dev.tomng.alarme.feature.settings.SettingsCallbacks
import dev.tomng.alarme.feature.settings.SettingsScreen
import dev.tomng.alarme.feature.settings.diagnostics.DiagnosticsViewModel
import dev.tomng.alarme.feature.settings.optimize.OemAutostartIntents
import dev.tomng.alarme.feature.settings.optimize.OemDetector
import dev.tomng.alarme.feature.settings.permissions.AndroidPermissionIntents
import dev.tomng.alarme.feature.settings.permissions.PermissionCenterViewModel
import dev.tomng.alarme.feature.settings.permissions.PermissionItem
import dev.tomng.alarme.feature.settings.reliability.ReliabilityViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var permissionIntents: AndroidPermissionIntents

    @Inject lateinit var autostartIntents: OemAutostartIntents

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AlarmeApp(permissionIntents, autostartIntents) }
    }
}

@Composable
private fun AlarmeApp(
    permissionIntents: AndroidPermissionIntents,
    autostartIntents: OemAutostartIntents,
    modifier: Modifier = Modifier,
) {
    val listViewModel: AlarmListViewModel = hiltViewModel()
    var screen by remember { mutableStateOf<Screen>(Screen.List) }

    AlarmeTheme {
        ScreenHost(
            screen = screen,
            parent = parentScreen(screen),
            onBack = { screen = it },
        ) { current ->
            when (current) {
                Screen.List ->
                    AlarmListScreen(
                        modifier = modifier,
                        viewModel = listViewModel,
                        onPickSound = { screen = Screen.Picker(PickerTarget.ForAlarm(it)) },
                        onPickDefault = { screen = Screen.Picker(PickerTarget.Default) },
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenEditor = { alarmId -> screen = Screen.Editor(alarmId) },
                    )

                is Screen.Picker ->
                    when (val target = current.target) {
                        is PickerTarget.ForAlarm ->
                            RingtonePickerScreen(
                                mode = PickerMode.Alarm,
                                selectedUri = target.alarm.ringtoneUri,
                                onPersist = { uri, _ ->
                                    listViewModel.setRingtone(target.alarm, uri)
                                    screen = Screen.List
                                },
                                onDone = { screen = Screen.List },
                                modifier = modifier,
                            )

                        PickerTarget.Default ->
                            RingtonePickerScreen(
                                mode = PickerMode.Default,
                                selectedUri = null,
                                onPersist = { _, _ -> },
                                onDone = { screen = Screen.List },
                                modifier = modifier,
                            )
                    }

                Screen.Settings ->
                    SettingsRoute(
                        permissionIntents = permissionIntents,
                        onOpenDiagnostics = { screen = Screen.Diagnostics },
                        onOpenOptimize = { screen = Screen.Optimize },
                        onOpenBackup = { screen = Screen.Backup },
                        onPickDefaultSound = { screen = Screen.Picker(PickerTarget.Default) },
                        onBack = { screen = Screen.List },
                        modifier = modifier,
                    )

                Screen.Diagnostics ->
                    DiagnosticsRoute(onBack = { screen = Screen.Settings }, modifier = modifier)

                Screen.Optimize ->
                    OptimizeRoute(
                        permissionIntents = permissionIntents,
                        autostartIntents = autostartIntents,
                        onBack = { screen = Screen.Settings },
                        modifier = modifier,
                    )

                Screen.Backup ->
                    BackupRoute(onBack = { screen = Screen.Settings }, modifier = modifier)

                is Screen.Editor ->
                    AlarmEditorRoute(
                        alarmId = current.alarmId,
                        onDone = { screen = Screen.List },
                        modifier = modifier,
                    )
            }
        }
    }
}

@Composable
private fun SettingsRoute(
    permissionIntents: AndroidPermissionIntents,
    onOpenDiagnostics: () -> Unit,
    onOpenOptimize: () -> Unit,
    onOpenBackup: () -> Unit,
    onPickDefaultSound: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissions: PermissionCenterViewModel = hiltViewModel()
    val reliability: ReliabilityViewModel = hiltViewModel()
    val permissionState by permissions.state.collectAsStateWithLifecycle()
    val reliabilityState by reliability.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Grants are made in system settings, so every return to this screen re-reads them.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissions.refresh()
        reliability.refresh()
    }

    SettingsScreen(
        permissions = permissionState,
        reliability = reliabilityState,
        callbacks =
            SettingsCallbacks(
                onOpenPermission = { item ->
                    runCatching { context.startActivity(permissionIntents.forItem(item)) }
                },
                onTogglePreventPowerOff = { enabled ->
                    reliability.setPreventPowerOff(enabled)
                    if (enabled) {
                        // Walk the user through whichever grant is still missing, in order.
                        val next =
                            when {
                                !reliabilityState.overlayGranted -> PermissionItem.OVERLAY
                                !reliabilityState.accessibilityGranted -> null
                                else -> null
                            }
                        if (next != null) {
                            runCatching { context.startActivity(permissionIntents.forItem(next)) }
                        } else if (!reliabilityState.accessibilityGranted) {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
                                    ),
                                )
                            }
                        }
                    }
                },
                onTogglePreventUninstall = { enabled ->
                    if (enabled) {
                        runCatching { context.startActivity(reliability.adminRequestIntent()) }
                    } else {
                        reliability.setPreventUninstall(false)
                    }
                },
                onOpenDiagnostics = onOpenDiagnostics,
                onOpenOptimize = onOpenOptimize,
                onOpenBackup = onOpenBackup,
                onOpenDefaultSound = onPickDefaultSound,
                onBack = onBack,
            ),
        modifier = modifier,
    )
}

@Composable
private fun DiagnosticsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DiagnosticsViewModel = hiltViewModel()
    val lines by viewModel.lines.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    DiagnosticsScreen(lines = lines, onBack = onBack, modifier = modifier)
}

@Composable
private fun OptimizeRoute(
    permissionIntents: AndroidPermissionIntents,
    autostartIntents: OemAutostartIntents,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissions: PermissionCenterViewModel = hiltViewModel()
    val state by permissions.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val guide = remember {
        OemDetector.detect(android.os.Build.MANUFACTURER, android.os.Build.BRAND)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissions.refresh() }

    OptimizeScreen(
        guide = guide,
        batteryUnrestricted =
            state.rows.singleOrNull { it.item == PermissionItem.BATTERY }?.granted == true,
        dndGranted = state.rows.singleOrNull { it.item == PermissionItem.DND }?.granted == true,
        onOpenBattery = {
            runCatching { context.startActivity(permissionIntents.forItem(PermissionItem.BATTERY)) }
        },
        onOpenDnd = {
            runCatching { context.startActivity(permissionIntents.forItem(PermissionItem.DND)) }
        },
        onOpenAutostart = {
            runCatching { context.startActivity(autostartIntents.resolve(guide)) }
        },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun BackupRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: BackupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip"),
        ) { uri ->
            if (uri != null) viewModel.export(uri)
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.import(uri)
        }

    BackupScreen(
        state = state,
        onExport = {
            viewModel.clear()
            exportLauncher.launch("alarme-backup-${java.time.LocalDate.now()}.zip")
        },
        onImport = {
            viewModel.clear()
            importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        },
        onBack = onBack,
        modifier = modifier,
    )
}
