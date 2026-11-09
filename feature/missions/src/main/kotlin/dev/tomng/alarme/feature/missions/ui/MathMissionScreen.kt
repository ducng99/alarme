package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.engine.MathMissionEngine

/**
 * The arithmetic mission. Difficulty 0 shows multiple-choice buttons; everything above uses a
 * keypad-style numeric field. Wrong answers cost nothing but time — the timer keeps running.
 */
@Composable
fun MathMissionScreen(
    mission: Mission.Math,
    callbacks: MissionStageCallbacks,
    engine: MathMissionEngine = remember { MathMissionEngine() },
) {
    var round by rememberSaveable { mutableStateOf(0) }
    var question by remember { mutableStateOf(engine.nextQuestion(mission.difficulty)) }
    var input by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }

    val answer: (Long?) -> Unit = { value ->
        wrong = value == null || !engine.check(question, value)
        if (!wrong) {
            if (round + 1 >= mission.rounds) {
                callbacks.onComplete()
            } else {
                round++
                question = engine.nextQuestion(mission.difficulty)
                input = ""
            }
        }
    }

    Column(
        modifier = Modifier.padding(16.dp).testTag("mission:math"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.mission_math_question, round + 1, mission.rounds),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = question.text,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.testTag("mission:math:question"),
        )

        val options = question.options
        if (options != null) {
            options.forEach { option ->
                OutlinedButton(
                    onClick = { answer(option) },
                    modifier = Modifier.widthIn(min = 160.dp).testTag("mission:math:option"),
                ) {
                    Text(option.toString())
                }
            }
        } else {
            OutlinedTextField(
                value = input,
                onValueChange = { value -> input = value.filter { it.isDigit() } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(0.6f).testTag("mission:math:input"),
            )
            Button(
                onClick = { answer(input.toLongOrNull()) },
                modifier = Modifier.testTag("mission:math:submit"),
            ) {
                Text(stringResource(R.string.mission_math_submit))
            }
        }

        if (wrong) {
            Text(
                text = stringResource(R.string.mission_math_wrong),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
