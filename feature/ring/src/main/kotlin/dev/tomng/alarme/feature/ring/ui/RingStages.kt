@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.tomng.alarme.feature.ring.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.alarmengine.statemachine.RingStage
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import dev.tomng.alarme.core.alarmengine.statemachine.RingStateMachine
import dev.tomng.alarme.core.designsystem.component.clockText
import dev.tomng.alarme.core.designsystem.component.rememberFeedback
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.MissionStageHost
import dev.tomng.alarme.feature.ring.R
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.delay

@Composable
internal fun PrepareContent(
    stage: RingStage.PrepareMission,
    total: Int,
    callbacks: RingScreenCallbacks,
) {
    StageCover(
        overline = missionProgress(stage.index, total),
        title = stringResource(R.string.ring_prepare_title),
        supporting = missionLabel(stage.mission),
        actionLabel = stringResource(R.string.ring_prepare_start),
        onAction = callbacks::onMissionStart,
    )
}

/** The mission stage: a header with the countdown (+10 s) above the mission's own screen. */
@Composable
internal fun MissionStageContent(
    stage: RingStage.Mission,
    total: Int,
    callbacks: RingScreenCallbacks,
) {
    val feedback = rememberFeedback()
    val missionCallbacks =
        remember(callbacks, stage.index) {
            object : MissionStageCallbacks {
                private var completed = false

                override fun onComplete() {
                    if (completed) return
                    completed = true
                    feedback.confirm()
                    callbacks.onMissionTimerStop()
                    callbacks.onMissionComplete()
                }

                override fun onTimerReset() = callbacks.onMissionTimerReset()

                override fun onTimerStop() = callbacks.onMissionTimerStop()
            }
        }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            MissionHeader(stage, total, callbacks)
            Box(modifier = Modifier.fillMaxSize()) {
                MissionStageHost(stage.mission, missionCallbacks)
            }
        }
    }
}

@Composable
private fun MissionHeader(stage: RingStage.Mission, total: Int, callbacks: RingScreenCallbacks) {
    val timer = stage.timer
    val remaining = timer?.remaining
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = missionProgress(stage.index, total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (timer != null && remaining != null) {
                Text(
                    text = countdownText(remaining),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.size(Spacing.s))
                val addLabel = stringResource(R.string.ring_mission_add_time)
                FilledTonalButton(
                    onClick = callbacks::onAddMissionTime,
                    modifier =
                        Modifier.heightIn(min = ButtonDefaults.MinHeight + Spacing.s)
                            .testTag("ring:add_time")
                            .semantics { contentDescription = addLabel },
                ) {
                    Text(stringResource(R.string.ring_mission_add_time_short))
                }
            }
        }
        if (timer != null && remaining != null) {
            val fraction =
                if (timer.total.isPositive()) (remaining / timer.total).toFloat().coerceIn(0f, 1f)
                else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
internal fun SnoozedContent(
    stage: RingStage.Snoozing,
    hasMissions: Boolean,
    callbacks: RingScreenCallbacks,
) {
    StageCover(
        title = stringResource(R.string.ring_snoozed_until, clockText(stage.finishAt)),
        supporting =
            if (hasMissions) stringResource(R.string.ring_snoozed_body_missions)
            else stringResource(R.string.ring_snoozed_body),
        actionLabel =
            if (hasMissions) stringResource(R.string.ring_snoozed_start_missions)
            else stringResource(R.string.ring_dismiss),
        onAction = callbacks::onDismiss,
        actionTag = "ring:dismiss",
    )
}

@Composable
internal fun EmergencyContent(remaining: Duration, callbacks: RingScreenCallbacks) {
    StageCover(
        title = stringResource(R.string.ring_emergency_title),
        supporting = stringResource(R.string.ring_emergency_body),
        actionLabel = stringResource(R.string.ring_emergency_done),
        onAction = callbacks::onEmergencyComplete,
        urgent = true,
    ) {
        Text(
            text = countdownText(remaining),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.error,
            modifier =
                Modifier.padding(top = Spacing.l).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * The quiet check screen: 100 s counting down, one way out. The countdown ticks on second
 * boundaries; reaching zero here only mirrors the executor, which owns the real timeout.
 */
@Composable
internal fun WakeUpCheckContent(
    state: RingState.WakeUpCheck,
    callbacks: RingScreenCallbacks,
    nowProvider: () -> Instant,
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
            val intoSecond = now.toEpochMilliseconds() % SECOND_MS
            delay(SECOND_MS - intoSecond)
        }
    }
    StageCover(
        title = stringResource(R.string.ring_wuc_title),
        supporting = stringResource(R.string.ring_wuc_hint),
        actionLabel = stringResource(R.string.ring_wuc_confirm),
        onAction = callbacks::onWakeUpCheckComplete,
        actionTag = "ring:wake_up_check_confirm",
    ) {
        val fraction =
            (remaining / RingStateMachine.WAKE_UP_CHECK_WINDOW).toFloat().coerceIn(0f, 1f)
        Box(
            modifier = Modifier.padding(top = Spacing.xl).size(WUC_RING),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxSize(),
            )
            Text(text = countdownText(remaining), style = MaterialTheme.typography.displayMedium)
        }
    }
}

/**
 * The centred layout shared by every non-ringing stage: the headline and one clear action at the
 * bottom.
 */
@Composable
private fun StageCover(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    supporting: String? = null,
    overline: String? = null,
    actionTag: String? = null,
    urgent: Boolean = false,
    middle: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        if (overline != null) {
            Text(
                text = overline,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
        middle()
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onAction,
            colors =
                if (urgent) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            modifier =
                Modifier.fillMaxWidth()
                    .widthIn(max = ACTION_MAX_WIDTH)
                    .heightIn(min = ButtonDefaults.LargeContainerHeight)
                    .then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
        ) {
            Text(text = actionLabel, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun missionProgress(index: Int, total: Int): String =
    stringResource(R.string.ring_mission_progress, index + 1, total.coerceAtLeast(index + 1))

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

private fun clockText(instant: Instant): String {
    val time =
        java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()).atZone(ZoneId.systemDefault())
    return clockText(time.hour, time.minute)
}

internal fun countdownText(remaining: Duration): String {
    val totalSeconds = remaining.inWholeSeconds
    return "%d:%02d".format(Locale.getDefault(), totalSeconds / 60, totalSeconds % 60)
}

private const val SECOND_MS = 1000L
private val WUC_RING = 184.dp
private val ACTION_MAX_WIDTH = 480.dp
