package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.GroupCard
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.engine.MathMissionEngine

/**
 * The arithmetic mission. Difficulty 0 shows multiple-choice buttons; everything above uses a
 * keypad-style numeric field. Wrong answers cost nothing but time - the timer keeps running.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MathMissionScreen(
    mission: Mission.Math,
    callbacks: MissionStageCallbacks,
    engine: MathMissionEngine = remember { MathMissionEngine() },
) {
    var round by rememberSaveable { mutableStateOf(0) }
    var question by remember { mutableStateOf(engine.nextQuestion(mission.difficulty)) }
    var input by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    var attempts by rememberSaveable { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

    val answer: (Long?) -> Unit = { value ->
        wrong = value == null || !engine.check(question, value)
        attempts++
        haptics.performHapticFeedback(
            if (wrong) HapticFeedbackType.Reject else HapticFeedbackType.Confirm,
        )
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

    MissionStageScaffold(
        title = stringResource(R.string.mission_math_title),
        icon = Icons.Outlined.Calculate,
        testTag = "mission:math",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.mission_math_question, round + 1, mission.rounds),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GroupCard(
                modifier = Modifier.offset(x = rememberShakeOffset(attempts.takeIf { wrong } ?: 0)),
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = question.text,
                        style = MaterialTheme.typography.displayMedium,
                        color =
                            if (wrong) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("mission:math:question"),
                    )
                }
            }

            val options = question.options
            if (options != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    options.forEach { option ->
                        FilledTonalButton(
                            onClick = { answer(option) },
                            shapes = ButtonDefaults.shapes(),
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .testTag("mission:math:option"),
                        ) {
                            Text(
                                text = option.toString(),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value -> input = value.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.mission_math_answer_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth().testTag("mission:math:input"),
                )
                Spacer(Modifier.height(4.dp))
            }

            if (wrong) {
                Text(
                    text = stringResource(R.string.mission_math_wrong),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (question.options == null) {
            MissionPrimaryButton(
                text = stringResource(R.string.mission_math_submit),
                onClick = { answer(input.toLongOrNull()) },
                modifier = Modifier.testTag("mission:math:submit"),
            )
        }
    }
}
