package dev.tomng.alarme.feature.ring.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.alarmengine.statemachine.RingStage
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import dev.tomng.alarme.core.alarmengine.statemachine.RingStateMachine
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
 * directly — the activity routes every intent into the session, and the session drives the UI.
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
        is RingState.Ringing -> RingingContent(state, callbacks, modifier)
        is RingState.WakeUpCheck -> WakeUpCheckContent(state, callbacks, modifier, nowProvider)
        RingState.Idle,
        is RingState.WakeUpCheckPending -> Unit
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
    modifier: Modifier,
    nowProvider: () -> kotlin.time.Instant,
) {
    var remaining by
        remember(state.startTime) { mutableStateOf(RingStateMachine.WAKE_UP_CHECK_WINDOW) }
    LaunchedEffect(state.startTime) {
        while (true) {
            val now = nowProvider()
            remaining =
                (RingStateMachine.WAKE_UP_CHECK_WINDOW - (now - state.startTime)).coerceAtLeast(
                    kotlin.time.Duration.ZERO,
                )
            val intoSecond = now.toEpochMilliseconds() % 1000
            delay(1000 - intoSecond)
        }
    }
    Surface(modifier = modifier.fillMaxSize().testTag("ring:content")) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.ring_wuc_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = countdownText(remaining),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = stringResource(R.string.ring_wuc_hint),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = callbacks::onWakeUpCheckComplete) {
                Text(stringResource(R.string.ring_wuc_confirm))
            }
        }
    }
}

@Composable
private fun RingingContent(
    state: RingState.Ringing,
    callbacks: RingScreenCallbacks,
    modifier: Modifier,
) {
    Surface(modifier = modifier.fillMaxSize().testTag("ring:content")) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val stage = state.stage) {
                is RingStage.Ring -> LoudAlarmContent(state, callbacks)
                is RingStage.PrepareMission -> PrepareContent(stage.mission, callbacks)
                is RingStage.Mission -> MissionStageContent(stage, callbacks)
                is RingStage.Snoozing ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.ring_snoozed_until, clockText(stage.finishAt)),
                        )
                        Button(
                            onClick = callbacks::onDismiss,
                            modifier = Modifier.padding(top = 24.dp).testTag("ring:dismiss"),
                        ) {
                            Text(stringResource(R.string.ring_dismiss))
                        }
                    }
                is RingStage.Emergency -> EmergencyContent(stage.remaining, callbacks)
            }
        }
    }
}

@Composable
private fun LoudAlarmContent(state: RingState.Ringing, callbacks: RingScreenCallbacks) {
    Text(text = state.alarm.emoji, style = MaterialTheme.typography.displayLarge)
    Text(text = state.alarm.name, style = MaterialTheme.typography.headlineMedium)
    Text(
        text = alarmTimeText(state.alarm.hour, state.alarm.minute),
        style = MaterialTheme.typography.displaySmall,
    )
    Button(onClick = callbacks::onDismiss) { Text(stringResource(R.string.ring_dismiss)) }
    if (snoozeOffered(state)) {
        OutlinedButton(onClick = callbacks::onSnooze) { Text(stringResource(R.string.ring_snooze)) }
    }
}

private fun snoozeOffered(state: RingState.Ringing): Boolean =
    state.alarm.snoozeOption.enabled && !state.snooze.exhausted

@Composable
private fun PrepareContent(mission: Mission, callbacks: RingScreenCallbacks) {
    Text(
        text = stringResource(R.string.ring_prepare_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(text = missionLabel(mission), style = MaterialTheme.typography.titleLarge)
    Button(onClick = callbacks::onMissionStart) {
        Text(stringResource(R.string.ring_prepare_start))
    }
}

/**
 * The mission stage: the countdown chrome (+10 s) above the mission's own screen. The screens
 * report completion and timer activity through the callbacks, never into the session directly.
 */
@Composable
private fun MissionStageContent(stage: RingStage.Mission, callbacks: RingScreenCallbacks) {
    val missionCallbacks =
        remember(callbacks) {
            object : MissionStageCallbacks {
                override fun onComplete() = callbacks.onMissionComplete()

                override fun onTimerReset() = callbacks.onMissionTimerReset()

                override fun onTimerStop() = callbacks.onMissionTimerStop()
            }
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val remaining = stage.timer?.remaining
        if (remaining != null) {
            Text(text = countdownText(remaining), style = MaterialTheme.typography.displaySmall)
            OutlinedButton(onClick = callbacks::onAddMissionTime) {
                Text(stringResource(R.string.ring_mission_add_time))
            }
        }
        MissionStageHost(mission = stage.mission, callbacks = missionCallbacks)
    }
}

@Composable
private fun EmergencyContent(remaining: Duration, callbacks: RingScreenCallbacks) {
    Text(
        text = stringResource(R.string.ring_emergency_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(text = countdownText(remaining), style = MaterialTheme.typography.displaySmall)
    Button(onClick = callbacks::onEmergencyComplete) {
        Text(stringResource(R.string.ring_emergency_done))
    }
}

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

private fun alarmTimeText(hour: Int, minute: Int): String =
    clockFormat.format(java.time.LocalTime.of(hour, minute))

private fun clockText(instant: kotlin.time.Instant): String =
    clockFormat.format(
        Instant.ofEpochMilli(instant.toEpochMilliseconds()).atZone(ZoneId.systemDefault()),
    )

internal fun countdownText(remaining: Duration): String {
    val totalSeconds = remaining.inWholeSeconds
    return "%d:%02d".format(Locale.getDefault(), totalSeconds / 60, totalSeconds % 60)
}
