package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.sensors.step.StepSensorTracker
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R

/**
 * The step mission: walk toward the goal as counted by the hardware step counter. Devices without
 * one fall back to the emergency path rather than a mission that can never end.
 */
@Composable
fun StepMissionScreen(
    mission: Mission.Step,
    callbacks: MissionStageCallbacks,
    trackerFactory: (android.content.Context) -> StepSensorTracker = { ctx ->
        StepSensorTracker.fromContext(ctx, mission.count)
    },
) {
    val context = LocalContext.current
    val tracker = remember { trackerFactory(context) }
    val progress by tracker.progress.collectAsState()
    val goal = mission.count.coerceAtLeast(1)

    DisposableEffect(tracker) {
        tracker.start()
        onDispose { tracker.stop() }
    }

    LaunchedEffect(progress.count) { if (progress.count > 0) callbacks.onTimerReset() }
    LaunchedEffect(progress.done) { if (progress.done) callbacks.onComplete() }

    MissionStageScaffold(
        title = stringResource(R.string.mission_step_title),
        testTag = "mission:step",
    ) {
        if (!tracker.stepCounterAvailable) {
            MissionUnavailableCard(text = stringResource(R.string.mission_step_no_sensor))
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                MissionProgressCard(
                    progress = progress.count.toFloat() / goal,
                    valueText =
                        stringResource(R.string.mission_step_progress, progress.count, goal),
                    caption = stringResource(R.string.mission_step_instruction, goal),
                    valueTag = "mission:step:count",
                    progressTag = "mission:step:progress",
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
