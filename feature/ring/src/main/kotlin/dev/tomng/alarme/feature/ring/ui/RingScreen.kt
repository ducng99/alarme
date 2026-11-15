package dev.tomng.alarme.feature.ring.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.AlarmOn
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tomng.alarme.core.alarmengine.statemachine.RingStage
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import dev.tomng.alarme.core.alarmengine.statemachine.RingStateMachine
import dev.tomng.alarme.core.designsystem.component.ClockTime
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.MissionStageHost
import dev.tomng.alarme.feature.ring.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * The ringing screen: one layout per stage of the live session. Nothing here touches the store
 * directly - the activity routes every intent into the session, and the session drives the UI.
 *
 * [nowProvider] feeds the wake-up-check countdown (second-aligned); the authoritative timeout is
 * the executor's window, this is only the display.
 */
@Composable
fun RingScreen(
    state: RingState,
    callbacks: RingScreenCallbacks,
    modifier: Modifier = Modifier,
    nowProvider: () -> kotlin.time.Instant = {
        kotlin.time.Instant.fromEpochMilliseconds(System.currentTimeMillis())
    },
) {
    when (state) {
        is RingState.Ringing ->
            when (val stage = state.stage) {
                is RingStage.Ring -> RingFrame(modifier) { LoudAlarmContent(state, callbacks) }
                is RingStage.PrepareMission ->
                    RingFrame(modifier) { PrepareContent(stage.mission, callbacks) }
                is RingStage.Mission -> MissionStageContent(stage, callbacks, modifier)
                is RingStage.Snoozing -> RingFrame(modifier) { SnoozedContent(stage, callbacks) }
                is RingStage.Emergency ->
                    RingFrame(modifier) { EmergencyContent(stage.remaining, callbacks) }
            }

        is RingState.WakeUpCheck ->
            RingFrame(modifier) { WakeUpCheckContent(state, callbacks, nowProvider) }

        RingState.Idle,
        is RingState.WakeUpCheckPending -> Unit
    }
}

@Composable
private fun RingFrame(modifier: Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = Color.Transparent, contentColor = colors.onSurface) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.primaryContainer,
                                colors.surfaceContainerLow,
                                colors.surface,
                            ),
                        ),
                    )
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .testTag("ring:content"),
        ) {
            content()
        }
    }
}

@Composable
private fun LoudAlarmContent(state: RingState.Ringing, callbacks: RingScreenCallbacks) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier.size(132.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            CircleShape,
                        ),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ) {
                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    Text(text = state.alarm.emoji, fontSize = 44.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ClockTime(
            hour = state.alarm.hour,
            minute = state.alarm.minute,
            style = ringClockStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (state.alarm.name.isNotBlank()) {
            Text(
                text = state.alarm.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (state.alarm.memoOption.memo.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Text(
                    text = state.alarm.memoOption.memo,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (snoozeOffered(state)) {
                FilledTonalButton(
                    onClick = callbacks::onSnooze,
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                ) {
                    Icon(Icons.Outlined.Snooze, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ring_snooze),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Button(
                onClick = callbacks::onDismiss,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("ring:dismiss"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ring_dismiss),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun snoozeOffered(state: RingState.Ringing): Boolean =
    state.alarm.snoozeOption.enabled && !state.snooze.exhausted

@Composable
private fun PrepareContent(mission: Mission, callbacks: RingScreenCallbacks) {
    StageCover(
        icon = Icons.Outlined.AlarmOff,
        title = stringResource(R.string.ring_prepare_title),
        supporting = missionLabel(mission),
        actionLabel = stringResource(R.string.ring_prepare_start),
        onAction = callbacks::onMissionStart,
    )
}

/**
 * The mission stage: the countdown chrome (+10 s) above the mission's own screen. The screens
 * report completion and timer activity through the callbacks, never into the session directly.
 */
@Composable
private fun MissionStageContent(
    stage: RingStage.Mission,
    callbacks: RingScreenCallbacks,
    modifier: Modifier = Modifier,
) {
    val missionCallbacks =
        remember(callbacks) {
            object : MissionStageCallbacks {
                override fun onComplete() = callbacks.onMissionComplete()

                override fun onTimerReset() = callbacks.onMissionTimerReset()

                override fun onTimerStop() = callbacks.onMissionTimerStop()
            }
        }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val remaining = stage.timer?.remaining
            if (remaining != null) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = countdownText(remaining),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = callbacks::onAddMissionTime,
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ring_mission_add_time),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                MissionStageHost(stage.mission, missionCallbacks)
            }
        }
    }
}

@Composable
private fun SnoozedContent(stage: RingStage.Snoozing, callbacks: RingScreenCallbacks) {
    StageCover(
        icon = Icons.Outlined.Snooze,
        title = stringResource(R.string.ring_snoozed_until, clockText(stage.finishAt)),
        actionLabel = stringResource(R.string.ring_dismiss),
        onAction = callbacks::onDismiss,
        actionTag = "ring:dismiss",
    )
}

@Composable
private fun EmergencyContent(remaining: Duration, callbacks: RingScreenCallbacks) {
    StageCover(
        icon = Icons.Outlined.AlarmOff,
        title = stringResource(R.string.ring_emergency_title),
        actionLabel = stringResource(R.string.ring_emergency_done),
        onAction = callbacks::onEmergencyComplete,
    ) {
        Text(
            text = countdownText(remaining),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * The quiet check screen: 100 s counting down, one way out. The countdown ticks on second
 * boundaries; reaching zero here only mirrors the executor, which owns the real timeout.
 */
@Composable
private fun WakeUpCheckContent(
    state: RingState.WakeUpCheck,
    callbacks: RingScreenCallbacks,
    nowProvider: () -> kotlin.time.Instant,
) {
    var remaining by
        remember(state.startTime) { mutableStateOf(RingStateMachine.WAKE_UP_CHECK_WINDOW) }
    LaunchedEffect(state.startTime) {
        while (true) {
            val now = nowProvider()
            remaining =
                (RingStateMachine.WAKE_UP_CHECK_WINDOW - (now - state.startTime)).coerceAtLeast(
                    Duration.ZERO,
                )
            val intoSecond = now.toEpochMilliseconds() % 1000
            delay(1000 - intoSecond)
        }
    }
    StageCover(
        icon = Icons.Outlined.AlarmOn,
        title = stringResource(R.string.ring_wuc_title),
        supporting = stringResource(R.string.ring_wuc_hint),
        actionLabel = stringResource(R.string.ring_wuc_confirm),
        onAction = callbacks::onWakeUpCheckComplete,
        actionTag = "ring:wake_up_check_confirm",
    ) {
        Text(
            text = countdownText(remaining),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

/**
 * The centered hero shared by every non-ringing stage: a soft icon medallion, the headline and one
 * clear action at the bottom.
 */
@Composable
private fun StageCover(
    icon: ImageVector,
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    actionTag: String? = null,
    middle: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StageMedallion(icon)
        Spacer(Modifier.height(20.dp))
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        middle()
        Button(
            onClick = onAction,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(top = 32.dp)
                    .heightIn(min = 56.dp)
                    .then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier),
        ) {
            Text(text = actionLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StageMedallion(icon: ImageVector) {
    Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier.size(132.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ) {
            Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ringClockStyle() =
    MaterialTheme.typography.displayLarge.copy(
        fontSize = 92.sp,
        lineHeight = 96.sp,
        letterSpacing = (-2).sp,
    )

@Composable
private fun missionLabel(mission: Mission): String =
    when (mission) {
        is Mission.Math -> stringResource(R.string.mission_label_math)
        is Mission.Memory -> stringResource(R.string.mission_label_memory)
        is Mission.Shake -> stringResource(R.string.mission_label_shake)
        is Mission.Step -> stringResource(R.string.mission_label_step)
        is Mission.Squat -> stringResource(R.string.mission_label_squat)
        is Mission.Typing -> stringResource(R.string.mission_label_typing)
        is Mission.Barcode -> stringResource(R.string.mission_label_barcode)
        is Mission.Photo -> stringResource(R.string.mission_label_photo)
        is Mission.ImageRecognition -> stringResource(R.string.mission_label_image)
        is Mission.WordBeat -> stringResource(R.string.mission_label_wordbeat)
    }

private val clockFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

private fun clockText(instant: kotlin.time.Instant): String =
    clockFormat.format(
        Instant.ofEpochMilli(instant.toEpochMilliseconds()).atZone(ZoneId.systemDefault()),
    )

internal fun countdownText(remaining: Duration): String {
    val totalSeconds = remaining.inWholeSeconds
    return "%d:%02d".format(Locale.getDefault(), totalSeconds / 60, totalSeconds % 60)
}
