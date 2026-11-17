package dev.tomng.alarme

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dagger.hilt.android.AndroidEntryPoint
import dev.tomng.alarme.core.designsystem.component.EmptyState
import dev.tomng.alarme.core.designsystem.theme.AlarmeTheme
import dev.tomng.alarme.feature.alarmlist.AlarmListScreen
import dev.tomng.alarme.feature.alarmlist.AlarmListViewModel
import dev.tomng.alarme.feature.backup.BackupScreen
import dev.tomng.alarme.feature.backup.BackupViewModel
import dev.tomng.alarme.feature.editor.AlarmEditorRoute
import dev.tomng.alarme.feature.editor.EditorSoundRequests
import dev.tomng.alarme.feature.editor.PickedSound
import dev.tomng.alarme.feature.ringtones.PickerMode
import dev.tomng.alarme.feature.ringtones.RingtonePickerScreen
import dev.tomng.alarme.feature.settings.DiagnosticsScreen
import dev.tomng.alarme.feature.settings.OptimizeScreen
import dev.tomng.alarme.feature.settings.PermissionsScreen
import dev.tomng.alarme.feature.settings.SettingsCallbacks
import dev.tomng.alarme.feature.settings.SettingsScreen
import dev.tomng.alarme.feature.settings.diagnostics.DiagnosticsViewModel
import dev.tomng.alarme.feature.settings.onboarding.OnboardingScreen
import dev.tomng.alarme.feature.settings.optimize.OemAutostartIntents
import dev.tomng.alarme.feature.settings.optimize.OemDetector
import dev.tomng.alarme.feature.settings.permissions.AndroidPermissionIntents
import dev.tomng.alarme.feature.settings.permissions.PermissionCenterState
import dev.tomng.alarme.feature.settings.permissions.PermissionCenterViewModel
import dev.tomng.alarme.feature.settings.permissions.PermissionItem
import dev.tomng.alarme.feature.settings.reliability.ReliabilityViewModel
import dev.tomng.alarme.feature.settings.sound.SoundSettingsViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var permissionIntents: AndroidPermissionIntents

    @Inject lateinit var autostartIntents: OemAutostartIntents

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AlarmeTheme { AlarmeApp(permissionIntents, autostartIntents) } }
    }
}

/** What every destination may need from the shell. */
private class AppDeps(
    val nav: NavState,
    val results: ResultBus,
    val permissions: PermissionCenterState,
    val listViewModel: AlarmListViewModel,
    val permissionIntents: AndroidPermissionIntents,
    val autostartIntents: OemAutostartIntents,
    val openPermission: (PermissionItem) -> Unit,
    val finishOnboarding: () -> Unit,
    val wide: Boolean,
)

@Composable
private fun AlarmeApp(
    permissionIntents: AndroidPermissionIntents,
    autostartIntents: OemAutostartIntents,
) {
    val appViewModel: AppViewModel = hiltViewModel()
    val listViewModel: AlarmListViewModel = hiltViewModel()
    val permissionViewModel: PermissionCenterViewModel = hiltViewModel()
    val onboardingDone by appViewModel.onboardingDone.collectAsStateWithLifecycle()
    val permissions by permissionViewModel.state.collectAsStateWithLifecycle()
    val nav = rememberSaveable(saver = NavState.Saver) { NavState() }
    val results = remember { ResultBus() }
    val openPermission = rememberPermissionOpener(permissionIntents)
    val wide = LocalWindowInfo.current.containerDpSize.width >= EXPANDED_WIDTH

    // Grants are made in system settings, so every return to the app re-reads them.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionViewModel.refresh() }
    LaunchedEffect(onboardingDone) {
        if (onboardingDone == false && nav.stack.none { it == Screen.Onboarding }) {
            nav.push(Screen.Onboarding)
        }
    }

    val deps =
        AppDeps(
            nav = nav,
            results = results,
            permissions = permissions,
            listViewModel = listViewModel,
            permissionIntents = permissionIntents,
            autostartIntents = autostartIntents,
            openPermission = openPermission,
            finishOnboarding = {
                appViewModel.finishOnboarding()
                if (nav.current == Screen.Onboarding) nav.pop()
            },
            wide = wide,
        )

    Surface(color = MaterialTheme.colorScheme.surface) {
        if (onboardingDone != null) {
            ScreenHost(
                nav = nav,
                containerTransforms = !wide,
                paneKey = { screen ->
                    if (wide && (screen == Screen.List || screen is Screen.Editor)) LIST_DETAIL_PANE
                    else screen.key
                },
            ) { screen ->
                Destination(screen, deps)
            }
        }
    }
}

/**
 * Opens the grant flow for one capability. Notifications use the runtime prompt first; when the
 * prompt is no longer offered the system notification settings open instead.
 */
@Composable
private fun rememberPermissionOpener(intents: AndroidPermissionIntents): (PermissionItem) -> Unit {
    val context = LocalContext.current
    val openSettings: (PermissionItem) -> Unit = { item ->
        runCatching { context.startActivity(intents.forItem(item)) }
    }
    val notificationPrompt =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) openSettings(PermissionItem.NOTIFICATIONS)
        }
    return { item ->
        if (item == PermissionItem.NOTIFICATIONS) {
            notificationPrompt.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openSettings(item)
        }
    }
}

@Composable
private fun Destination(screen: Screen, deps: AppDeps) {
    val nav = deps.nav
    when (screen) {
        Screen.List -> if (deps.wide) ListDetail(deps, editorId = null) else ListRoute(deps)

        is Screen.Editor ->
            if (deps.wide) ListDetail(deps, editorId = screen.alarmId)
            else EditorRoute(screen.alarmId, deps)

        Screen.Onboarding ->
            OnboardingScreen(
                permissions = deps.permissions,
                welcome = true,
                onAllow = deps.openPermission,
                onFinish = deps.finishOnboarding,
            )

        is Screen.Picker -> PickerRoute(screen.target, deps)

        Screen.Settings -> SettingsRoute(deps)

        Screen.Permissions ->
            PermissionsScreen(
                permissions = deps.permissions,
                onOpenPermission = deps.openPermission,
                onBack = { nav.pop() },
            )

        Screen.Diagnostics -> DiagnosticsRoute(onBack = { nav.pop() })

        Screen.Optimize -> OptimizeRoute(deps)

        Screen.Backup -> BackupRoute(onBack = { nav.pop() })
    }
}

@Composable
private fun ListRoute(deps: AppDeps, modifier: Modifier = Modifier, selectedId: Long? = null) {
    val nav = deps.nav
    AlarmListScreen(
        modifier = modifier,
        viewModel = deps.listViewModel,
        criticalMissing =
            deps.permissions.rows.isNotEmpty() && deps.permissions.criticalMissing.isNotEmpty(),
        selectedId = selectedId,
        onFixPermissions = { nav.push(Screen.Onboarding) },
        onPickSound = { nav.push(Screen.Picker(PickerTarget.ForAlarm(it.id))) },
        onOpenSettings = { nav.push(Screen.Settings) },
        onOpenEditor = { alarmId ->
            if (deps.wide && nav.current is Screen.Editor) nav.replaceTop(Screen.Editor(alarmId))
            else nav.push(Screen.Editor(alarmId))
        },
    )
}

@Composable
private fun EditorRoute(alarmId: Long, deps: AppDeps, modifier: Modifier = Modifier) {
    val nav = deps.nav
    val pending by deps.results.results.collectAsStateWithLifecycle()
    val soundSlot = PickerTarget.EditorSound(alarmId).slot
    val backupSlot = PickerTarget.EditorBackupSound(alarmId).slot
    val picked =
        pending
            .firstOrNull { it.slot == soundSlot }
            ?.let { PickedSound(backup = false, uri = it.uri) }
            ?: pending
                .firstOrNull { it.slot == backupSlot }
                ?.let { PickedSound(backup = true, uri = it.uri) }
    AlarmEditorRoute(
        alarmId = alarmId,
        onDone = { nav.pop() },
        sounds =
            EditorSoundRequests(
                picked = picked,
                onConsumed = { backup -> deps.results.take(if (backup) backupSlot else soundSlot) },
                onPickSound = { uri ->
                    nav.push(Screen.Picker(PickerTarget.EditorSound(alarmId, uri)))
                },
                onPickBackupSound = { uri ->
                    nav.push(Screen.Picker(PickerTarget.EditorBackupSound(alarmId, uri)))
                },
            ),
        onFixPermissions = { nav.push(Screen.Onboarding) },
        modifier = modifier,
    )
}

/** Expanded widths keep the list beside the editor instead of replacing it. */
@Composable
private fun ListDetail(deps: AppDeps, editorId: Long?) {
    Row(modifier = Modifier.fillMaxSize()) {
        ListRoute(
            deps,
            modifier = Modifier.width(LIST_PANE_WIDTH).fillMaxHeight(),
            selectedId = editorId,
        )
        VerticalDivider()
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (editorId != null) {
                key(editorId) {
                    val owner =
                        checkNotNull(LocalEntryStores.current).owner(Screen.Editor(editorId).key)
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        EditorRoute(editorId, deps)
                    }
                }
            } else {
                EmptyDetail()
            }
        }
    }
}

@Composable
private fun EmptyDetail() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Outlined.Alarm,
            title = stringResource(R.string.app_detail_empty_title),
            body = stringResource(R.string.app_detail_empty_body),
        )
    }
}

@Composable
private fun PickerRoute(target: PickerTarget, deps: AppDeps) {
    val nav = deps.nav
    val alarms by deps.listViewModel.alarms.collectAsStateWithLifecycle()
    when (target) {
        is PickerTarget.ForAlarm -> {
            val alarm = alarms?.firstOrNull { it.id == target.alarmId }
            RingtonePickerScreen(
                mode = PickerMode.Alarm,
                selectedUri = alarm?.ringtoneUri,
                onPersist = { uri, _ -> alarm?.let { deps.listViewModel.setRingtone(it, uri) } },
                onDone = { nav.pop() },
            )
        }
        is PickerTarget.EditorSound ->
            RingtonePickerScreen(
                mode = PickerMode.Alarm,
                selectedUri = target.currentUri,
                onPersist = { uri, _ -> deps.results.send(SoundResult(target.slot, uri)) },
                onDone = { nav.pop() },
            )
        is PickerTarget.EditorBackupSound ->
            RingtonePickerScreen(
                mode = PickerMode.Backup,
                selectedUri = target.currentUri,
                onPersist = { uri, _ -> deps.results.send(SoundResult(target.slot, uri)) },
                onDone = { nav.pop() },
            )
        PickerTarget.Default ->
            RingtonePickerScreen(
                mode = PickerMode.Default,
                selectedUri = null,
                onPersist = { _, _ -> },
                onDone = { nav.pop() },
            )
    }
}

@Composable
private fun SettingsRoute(deps: AppDeps) {
    val nav = deps.nav
    val reliability: ReliabilityViewModel = hiltViewModel()
    val reliabilityState by reliability.state.collectAsStateWithLifecycle()
    val sound: SoundSettingsViewModel = hiltViewModel()
    val soundState by sound.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reliability.refresh() }

    SettingsScreen(
        permissions = deps.permissions,
        reliability = reliabilityState,
        sound = soundState,
        callbacks =
            SettingsCallbacks(
                onOpenPermission = deps.openPermission,
                onOpenPermissions = { nav.push(Screen.Permissions) },
                onFixPermissions = { nav.push(Screen.Onboarding) },
                onTogglePreventPowerOff = { enabled ->
                    reliability.setPreventPowerOff(enabled)
                    if (enabled) {
                        // Walk the user through whichever grant is still missing, in order.
                        if (!reliabilityState.overlayGranted) {
                            deps.openPermission(PermissionItem.OVERLAY)
                        } else if (!reliabilityState.accessibilityGranted) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
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
                onOpenDiagnostics = { nav.push(Screen.Diagnostics) },
                onOpenOptimize = { nav.push(Screen.Optimize) },
                onOpenBackup = { nav.push(Screen.Backup) },
                onOpenDefaultSound = { nav.push(Screen.Picker(PickerTarget.Default)) },
                onToggleMuteDuringMission = sound::setMuteDuringMission,
                onBack = { nav.pop() },
            ),
    )
}

@Composable
private fun DiagnosticsRoute(onBack: () -> Unit) {
    val viewModel: DiagnosticsViewModel = hiltViewModel()
    val lines by viewModel.lines.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    DiagnosticsScreen(lines = lines, onBack = onBack)
}

@Composable
private fun OptimizeRoute(deps: AppDeps) {
    val context = LocalContext.current
    val guide = remember {
        OemDetector.detect(android.os.Build.MANUFACTURER, android.os.Build.BRAND)
    }

    OptimizeScreen(
        guide = guide,
        batteryUnrestricted = deps.permissions.isGranted(PermissionItem.BATTERY),
        dndGranted = deps.permissions.isGranted(PermissionItem.DND),
        onOpenBattery = { deps.openPermission(PermissionItem.BATTERY) },
        onOpenDnd = { deps.openPermission(PermissionItem.DND) },
        onOpenAutostart = {
            runCatching { context.startActivity(deps.autostartIntents.resolve(guide)) }
        },
        onBack = { deps.nav.pop() },
    )
}

@Composable
private fun BackupRoute(onBack: () -> Unit) {
    val viewModel: BackupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    )
}

private val LIST_PANE_WIDTH = 400.dp
private val EXPANDED_WIDTH = 840.dp
private const val LIST_DETAIL_PANE = "list-detail"
