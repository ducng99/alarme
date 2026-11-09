package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.engine.WordBeatMatcher
import dev.tomng.alarme.feature.missions.engine.WordBeatRounds
import dev.tomng.alarme.feature.missions.speech.AndroidSpeechSource
import dev.tomng.alarme.feature.missions.speech.SpeechEvent
import dev.tomng.alarme.feature.missions.speech.SpeechSource
import kotlin.random.Random

/**
 * The spoken-word mission: read the shown sentence, and at least 25 % of its tokens must come back
 * from the recogniser. No engine for the language means a dead end with only the emergency path
 * out.
 */
@Composable
fun WordBeatMissionScreen(
    mission: Mission.WordBeat,
    callbacks: MissionStageCallbacks,
    speechFactory: (android.content.Context) -> SpeechSource = { AndroidSpeechSource(it) },
    seed: Long = remember { Random.nextLong() },
) {
    val context = LocalContext.current
    val source = remember { speechFactory(context) }
    val event by source.events.collectAsState()
    val rounds =
        remember(mission, seed) { WordBeatRounds.build(mission.language, mission.rounds, seed) }
    var round by rememberSaveable { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<SpeechEvent>(SpeechEvent.Idle) }

    val locale = mission.language.sttLocale
    val available = remember(source) { source.isLanguageAvailable(locale) }

    DisposableEffect(source) { onDispose { source.close() } }

    LaunchedEffect(event) {
        val current = event
        if (current is SpeechEvent.Idle) return@LaunchedEffect
        feedback = current
        if (current is SpeechEvent.Unavailable) return@LaunchedEffect
        if (current is SpeechEvent.Recognized) {
            val target = rounds.getOrNull(round) ?: return@LaunchedEffect
            if (WordBeatMatcher.passes(target, current.text, mission.language.tokenMode)) {
                if (round + 1 >= rounds.size) {
                    callbacks.onComplete()
                } else {
                    round++
                    feedback = SpeechEvent.Idle
                }
            }
        }
    }

    Column(
        modifier = Modifier.padding(16.dp).testTag("mission:wordbeat"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            !available || feedback is SpeechEvent.Unavailable ->
                Text(
                    text = stringResource(R.string.mission_wordbeat_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                )

            else -> {
                Text(
                    text = stringResource(R.string.mission_wordbeat_round, round + 1, rounds.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.mission_wordbeat_instruction),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = rounds.getOrNull(round).orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("mission:wordbeat:target"),
                )
                Text(
                    text =
                        when (feedback) {
                            is SpeechEvent.Recognized ->
                                stringResource(R.string.mission_wordbeat_try_again)

                            is SpeechEvent.NoSpeech ->
                                stringResource(R.string.mission_wordbeat_try_again)

                            else -> stringResource(R.string.mission_wordbeat_listening)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (
                            feedback is SpeechEvent.Recognized || feedback is SpeechEvent.NoSpeech
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier = Modifier.testTag("mission:wordbeat:status"),
                )
                Button(
                    onClick = { source.startListening(locale) },
                    modifier = Modifier.testTag("mission:wordbeat:listen"),
                ) {
                    Text(stringResource(R.string.mission_wordbeat_listening))
                }
            }
        }
    }
}
