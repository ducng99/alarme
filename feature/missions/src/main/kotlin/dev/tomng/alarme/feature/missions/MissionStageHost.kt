package dev.tomng.alarme.feature.missions

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.ui.BarcodeMissionScreen
import dev.tomng.alarme.feature.missions.ui.ImageRecognitionMissionScreen
import dev.tomng.alarme.feature.missions.ui.MathMissionScreen
import dev.tomng.alarme.feature.missions.ui.MemoryMissionScreen
import dev.tomng.alarme.feature.missions.ui.PhotoMissionScreen
import dev.tomng.alarme.feature.missions.ui.ShakeMissionScreen
import dev.tomng.alarme.feature.missions.ui.SquatMissionScreen
import dev.tomng.alarme.feature.missions.ui.StepMissionScreen
import dev.tomng.alarme.feature.missions.ui.TypingMissionScreen
import dev.tomng.alarme.feature.missions.ui.WordBeatMissionScreen

/**
 * The mission stage entry point: renders the screen for the mission currently in the chain. Each
 * screen completes itself and reports through [callbacks]; none of them touch the session store
 * directly.
 */
@Composable
fun MissionStageHost(
    mission: Mission,
    callbacks: MissionStageCallbacks,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        when (mission) {
            is Mission.Math -> MathMissionScreen(mission, callbacks)
            is Mission.Memory -> MemoryMissionScreen(mission, callbacks)
            is Mission.Typing -> TypingMissionScreen(mission, callbacks)
            is Mission.Shake -> ShakeMissionScreen(mission, callbacks)
            is Mission.Squat -> SquatMissionScreen(mission, callbacks)
            is Mission.Step -> StepMissionScreen(mission, callbacks)
            is Mission.Barcode -> BarcodeMissionScreen(mission, callbacks)
            is Mission.Photo -> PhotoMissionScreen(mission, callbacks)
            is Mission.ImageRecognition -> ImageRecognitionMissionScreen(mission, callbacks)
            is Mission.WordBeat -> WordBeatMissionScreen(mission, callbacks)
        }
    }
}
