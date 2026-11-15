package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.engine.MemoryMissionEngine
import kotlin.time.Duration.Companion.milliseconds

private val MEMORIZE_MILLIS = 1_500.milliseconds
private val ROUND_CLEAR_MILLIS = 900.milliseconds

/**
 * The colour-tile game. The grid flashes the correct tiles (memorize), hides them, then the player
 * reproduces the set; wrong taps latch as visible mistakes.
 */
@Composable
fun MemoryMissionScreen(
    mission: Mission.Memory,
    callbacks: MissionStageCallbacks,
    engine: MemoryMissionEngine = remember { MemoryMissionEngine() },
) {
    var state by remember { mutableStateOf(engine.start(mission)) }

    // The memorize flash, then a beat of nothing, then play.
    LaunchedEffect(state.round) {
        if (state.phase == MemoryMissionEngine.Phase.MEMORIZING) {
            kotlinx.coroutines.delay(MEMORIZE_MILLIS)
            state = engine.beginPlaying(state)
        }
    }
    LaunchedEffect(state.phase) {
        if (state.phase == MemoryMissionEngine.Phase.ROUND_CLEAR) {
            kotlinx.coroutines.delay(ROUND_CLEAR_MILLIS)
            state = engine.nextRound(state)
            if (state.phase == MemoryMissionEngine.Phase.COMPLETE) callbacks.onComplete()
        }
    }

    MissionStageScaffold(
        title = stringResource(R.string.mission_memory_title),
        icon = Icons.Outlined.Psychology,
        testTag = "mission:memory",
    ) {
        Text(
            text =
                when (state.phase) {
                    MemoryMissionEngine.Phase.MEMORIZING ->
                        stringResource(R.string.mission_memory_memorize)

                    MemoryMissionEngine.Phase.PLAYING ->
                        if (state.board.remaining > 0) {
                            stringResource(R.string.mission_memory_n_left, state.board.remaining)
                        } else {
                            stringResource(R.string.mission_memory_found_all)
                        }

                    MemoryMissionEngine.Phase.ROUND_CLEAR ->
                        stringResource(R.string.mission_memory_round_clear, state.round + 1)

                    else -> stringResource(R.string.mission_memory_find)
                },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("mission:memory:header"),
        )

        val columns = state.board.difficulty
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("mission:memory:grid"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.board.tiles.size) { index ->
                val tile = state.board.tiles[index]
                val flashCorrect =
                    state.phase == MemoryMissionEngine.Phase.MEMORIZING && tile.isCorrect
                val color =
                    when {
                        flashCorrect -> MaterialTheme.colorScheme.primary
                        tile.state == MemoryMissionEngine.TileState.CORRECT ->
                            MaterialTheme.colorScheme.primary

                        tile.state == MemoryMissionEngine.TileState.WRONG ->
                            MaterialTheme.colorScheme.error

                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                Button(
                    onClick = { state = engine.tap(state, index) },
                    enabled = state.phase == MemoryMissionEngine.Phase.PLAYING,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.aspectRatio(1f).testTag("mission:memory:tile"),
                ) {}
            }
        }
    }
}
