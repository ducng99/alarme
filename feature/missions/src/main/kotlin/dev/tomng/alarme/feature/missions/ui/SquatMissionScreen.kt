package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.sensors.squat.CoverSensorTracker
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R

/**
 * The squat mission: cover the sensor, lift, repeat. Without a light or proximity sensor the
 * mission cannot complete - the screen says so and leans on the emergency path.
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

    MissionStageScaffold(
        title = stringResource(R.string.mission_squat_title),
        icon = Icons.Outlined.FitnessCenter,
        testTag = "mission:squat",
    ) {
        when (tracker.availability) {
            CoverSensorTracker.Availability.NO_SENSOR ->
                MissionUnavailableCard(text = stringResource(R.string.mission_squat_no_sensor))

            CoverSensorTracker.Availability.AVAILABLE -> {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    MissionProgressCard(
                        progress = progress.reps.toFloat() / goal,
                        valueText =
                            stringResource(R.string.mission_shake_progress, progress.reps, goal),
                        caption = stringResource(R.string.mission_squat_instruction, goal),
                        valueTag = "mission:squat:count",
                        progressTag = "mission:squat:progress",
                    )
                    if (progress.covered) {
                        Text(
                            text = stringResource(R.string.mission_squat_covered),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (progress.cheat) {
                        Text(
                            text = stringResource(R.string.mission_squat_cheat),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
