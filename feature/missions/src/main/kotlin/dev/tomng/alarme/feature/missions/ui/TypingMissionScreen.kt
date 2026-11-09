package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.engine.TypingMissionEngine
import kotlin.random.Random

/**
 * The typing mission: the target sentence is shown with the typed prefix highlighted; every
 * keystroke resets the mission timer so the timeout never fires mid-sentence, and Enter (or the
 * button) submits the round.
 */
@Composable
fun TypingMissionScreen(
    mission: Mission.Typing,
    callbacks: MissionStageCallbacks,
    randomSeed: Long = remember { Random.nextLong() },
) {
    val sentences = mission.typingSentences.map { it.sentence }.ifEmpty { DEFAULT_SENTENCES }
    val rounds = TypingMissionEngine.buildRotation(sentences, mission.rounds, Random(randomSeed))
    var index by rememberSaveable { mutableIntStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }

    val target = rounds.getOrNull(index) ?: sentences.first()
    val progress = TypingMissionEngine.progress(target, input)
    val roundComplete = progress.complete

    fun advance() {
        if (index + 1 >= rounds.size) {
            callbacks.onComplete()
        } else {
            index++
            input = ""
        }
    }

    val answer = { if (roundComplete) advance() }

    Column(
        modifier = Modifier.padding(16.dp).testTag("mission:typing"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.mission_typing_prompt),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text =
                buildAnnotatedString {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(progress.matchedPrefix)
                    pop()
                    append(progress.remaining)
                },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("mission:typing:target"),
        )
        OutlinedTextField(
            value = input,
            onValueChange = { value ->
                input = value
                // Each keystroke is user activity: restart the countdown.
                callbacks.onTimerReset()
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { answer() }),
            modifier = Modifier.fillMaxWidth().testTag("mission:typing:input"),
        )
        Button(
            onClick = answer,
            enabled = roundComplete,
            modifier = Modifier.testTag("mission:typing:submit"),
        ) {
            Text(stringResource(R.string.mission_typing_submit))
        }
    }
}

private val DEFAULT_SENTENCES =
    listOf(
        "The early bird catches the worm",
        "A new day is a fresh start",
        "Rise and shine, sleepy head",
    )
