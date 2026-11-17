package dev.tomng.alarme.feature.alarmlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomng.alarme.core.designsystem.component.AlarmeScaffold
import dev.tomng.alarme.core.designsystem.component.BannerTone
import dev.tomng.alarme.core.designsystem.component.EmptyState
import dev.tomng.alarme.core.designsystem.component.InlineBanner
import dev.tomng.alarme.core.designsystem.component.alarmContainerKey
import dev.tomng.alarme.core.designsystem.component.durationText
import dev.tomng.alarme.core.designsystem.component.rememberListPadding
import dev.tomng.alarme.core.designsystem.component.rememberMinuteTicker
import dev.tomng.alarme.core.designsystem.component.sharedContainer
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.alarm.Alarm
import kotlin.time.Instant

/** The alarm list: the next ring in the app bar, alarm cards, and the new/quick alarm buttons. */
@Composable
fun AlarmListScreen(
    modifier: Modifier = Modifier,
    viewModel: AlarmListViewModel = hiltViewModel(),
    criticalMissing: Boolean = false,
    selectedId: Long? = null,
    onFixPermissions: () -> Unit = {},
    onPickSound: (Alarm) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenEditor: (Long) -> Unit = {},
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val nextAlarm by viewModel.nextAlarm.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    MessageEffect(
        message = message,
        snackbar = snackbar,
        onUndo = { shown ->
            when (shown) {
                AlarmListMessage.Deleted -> viewModel.undoDelete()
                is AlarmListMessage.Skipped -> viewModel.undoSkip(shown.alarmId)
                is AlarmListMessage.QuickCreated -> viewModel.undoQuick()
                else -> Unit
            }
        },
        onFix = onFixPermissions,
        onConsumed = viewModel::consumeMessage,
    )

    AlarmListContent(
        alarms = alarms,
        nextAlarm = nextAlarm,
        snackbarHostState = snackbar,
        criticalMissing = criticalMissing,
        selectedId = selectedId,
        callbacks =
            AlarmListCallbacks(
                onToggle = viewModel::setEnabled,
                onSkip = viewModel::skip,
                onUndoSkip = viewModel::undoSkip,
                onDelete = viewModel::delete,
                onQuickCreate = viewModel::createQuick,
                onPickSound = onPickSound,
                onOpenSettings = onOpenSettings,
                onOpenEditor = onOpenEditor,
                onFixPermissions = onFixPermissions,
            ),
        modifier = modifier,
    )
}

@Composable
private fun MessageEffect(
    message: AlarmListMessage?,
    snackbar: SnackbarHostState,
    onUndo: (AlarmListMessage) -> Unit,
    onFix: () -> Unit,
    onConsumed: () -> Unit,
) {
    val text = message?.let { messageText(it) }
    val undo = stringResource(R.string.alarmlist_undo)
    val fix = stringResource(R.string.alarmlist_fix)
    LaunchedEffect(message) {
        if (message == null || text == null) return@LaunchedEffect
        val action =
            when (message) {
                AlarmListMessage.Deleted,
                is AlarmListMessage.Skipped,
                is AlarmListMessage.QuickCreated -> undo
                AlarmListMessage.NotArmed -> fix
                else -> null
            }
        val result =
            snackbar.showSnackbar(
                message = text,
                actionLabel = action,
                withDismissAction = action != null,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        if (result == SnackbarResult.ActionPerformed) {
            if (message == AlarmListMessage.NotArmed) onFix() else onUndo(message)
        }
        onConsumed()
    }
}

@Composable
private fun messageText(message: AlarmListMessage): String {
    val now = rememberMinuteTicker()
    return when (message) {
        AlarmListMessage.SkipDisabled -> stringResource(R.string.alarmlist_skip_disabled)
        AlarmListMessage.SkipNotRepeating -> stringResource(R.string.alarmlist_skip_not_repeating)
        AlarmListMessage.QuickNotEditable -> stringResource(R.string.alarmlist_quick_not_editable)
        AlarmListMessage.NotArmed -> stringResource(R.string.alarmlist_not_armed)
        AlarmListMessage.Deleted -> stringResource(R.string.alarmlist_deleted)
        is AlarmListMessage.Skipped -> stringResource(R.string.alarmlist_skipped)
        is AlarmListMessage.TurnedOn ->
            stringResource(R.string.alarmlist_turned_on, durationText(now, message.at))
        is AlarmListMessage.QuickCreated ->
            stringResource(R.string.alarmlist_quick_created, durationText(now, message.at))
    }
}

/** The user intents the list emits. */
class AlarmListCallbacks(
    val onToggle: (Alarm, Boolean) -> Unit,
    val onSkip: (Alarm) -> Unit,
    val onUndoSkip: (Alarm) -> Unit,
    val onDelete: (Alarm) -> Unit,
    val onQuickCreate: (minutes: Int) -> Unit,
    val onPickSound: (Alarm) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenEditor: (Long) -> Unit = {},
    val onFixPermissions: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlarmListContent(
    alarms: List<Alarm>?,
    nextAlarm: Alarm?,
    callbacks: AlarmListCallbacks,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    criticalMissing: Boolean = false,
    selectedId: Long? = null,
) {
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { !listState.canScrollBackward } }
    var quickOpen by remember { mutableStateOf(false) }
    val now = rememberMinuteTicker()

    if (quickOpen) {
        QuickAlarmSheet(
            onDismiss = { quickOpen = false },
            onCreate = { minutes ->
                quickOpen = false
                callbacks.onQuickCreate(minutes)
            },
        )
    }

    AlarmeScaffold(
        title = "Alarme",
        collapsible = false,
        subtitle = nextSubtitle(nextAlarm, alarms, now),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(
                onClick = callbacks.onOpenSettings,
                modifier = Modifier.testTag("alarmlist:settings"),
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.alarmlist_settings),
                )
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                SmallFloatingActionButton(
                    onClick = { quickOpen = true },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.testTag("alarmlist:quick"),
                ) {
                    Icon(
                        Icons.Outlined.Bolt,
                        contentDescription = stringResource(R.string.alarmlist_quick_open),
                    )
                }
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.alarmlist_new_alarm)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { callbacks.onOpenEditor(Alarm.NEW_ID) },
                    expanded = fabExpanded,
                    modifier =
                        Modifier.sharedContainer(
                                alarmContainerKey(Alarm.NEW_ID),
                                MaterialTheme.shapes.large,
                            )
                            .testTag("alarmlist:new"),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("alarmlist:list"),
            contentPadding =
                rememberListPadding(
                    innerPadding,
                    horizontal = Spacing.screen,
                    bottom = FAB_CLEARANCE,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item(key = "banner") {
                AnimatedVisibility(visible = criticalMissing) {
                    InlineBanner(
                        title = stringResource(R.string.alarmlist_banner_title),
                        body = stringResource(R.string.alarmlist_banner_body),
                        icon = Icons.Outlined.PriorityHigh,
                        tone = BannerTone.Error,
                        actionLabel = stringResource(R.string.alarmlist_fix),
                        onAction = callbacks.onFixPermissions,
                        actionModifier = Modifier.testTag("alarmlist:fix"),
                        modifier = Modifier.padding(bottom = Spacing.s),
                    )
                }
            }
            if (alarms?.isEmpty() == true) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.Bedtime,
                        title = stringResource(R.string.alarmlist_no_alarms),
                        body = stringResource(R.string.alarmlist_empty_hint),
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            items(alarms.orEmpty(), key = { it.id }) { alarm ->
                SwipeableAlarmCard(
                    alarm = alarm,
                    now = now,
                    selected = alarm.id == selectedId,
                    callbacks = callbacks,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** The app bar subtitle: when the next alarm rings, or why nothing will. */
@Composable
private fun nextSubtitle(nextAlarm: Alarm?, alarms: List<Alarm>?, now: Instant): String? {
    val at = nextAlarm?.nextOccurrence
    return when {
        at != null -> stringResource(R.string.alarmlist_next_in, durationText(now, at))
        !alarms.isNullOrEmpty() -> stringResource(R.string.alarmlist_all_off)
        else -> null
    }
}

private val FAB_CLEARANCE = Spacing.xxxl * 4
