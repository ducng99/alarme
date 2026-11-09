package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

    LaunchedEffect(progress.done) { if (progress.done) callbacks.onComplete() }

    Column(
        modifier = Modifier.padding(16.dp).testTag("mission:step"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!tracker.stepCounterAvailable) {
            Text(
                text = stringResource(R.string.mission_step_no_sensor),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(
                text = stringResource(R.string.mission_step_instruction, goal),
                style = MaterialTheme.typography.headlineSmall,
            )
            LinearProgressIndicator(
                progress = { progress.count.toFloat() / goal },
                modifier = Modifier.fillMaxWidth().height(12.dp).testTag("mission:step:progress"),
            )
            Text(
                text = stringResource(R.string.mission_step_progress, progress.count, goal),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("mission:step:count"),
            )
        }
    }
}
