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
import dev.tomng.alarme.core.sensors.squat.CoverSensorTracker
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R

/**
 * The squat mission: cover the sensor, lift, repeat. Without a light or proximity sensor the
 * mission cannot complete — the screen says so and leans on the emergency path.
 */
@Composable
fun SquatMissionScreen(
    mission: Mission.Squat,
    callbacks: MissionStageCallbacks,
    trackerFactory: (android.content.Context) -> CoverSensorTracker = { ctx ->
        CoverSensorTracker.fromContext(ctx, mission.count)
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
        modifier = Modifier.padding(16.dp).testTag("mission:squat"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (tracker.availability) {
            CoverSensorTracker.Availability.NO_SENSOR ->
                Text(
                    text = stringResource(R.string.mission_squat_no_sensor),
                    style = MaterialTheme.typography.bodyLarge,
                )

            CoverSensorTracker.Availability.AVAILABLE -> {
                Text(
                    text = stringResource(R.string.mission_squat_instruction, goal),
                    style = MaterialTheme.typography.headlineSmall,
                )
                LinearProgressIndicator(
                    progress = { progress.reps.toFloat() / goal },
                    modifier =
                        Modifier.fillMaxWidth().height(12.dp).testTag("mission:squat:progress"),
                )
                Text(
                    text = stringResource(R.string.mission_shake_progress, progress.reps, goal),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("mission:squat:count"),
                )
                if (progress.covered) {
                    Text(text = stringResource(R.string.mission_squat_covered))
                }
                if (progress.cheat) {
                    Text(
                        text = stringResource(R.string.mission_squat_cheat),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
