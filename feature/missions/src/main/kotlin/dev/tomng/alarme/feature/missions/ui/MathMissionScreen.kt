package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.engine.MathMissionEngine

/**
 * The arithmetic mission. Difficulty 0 shows multiple-choice buttons; everything above uses a
 * built-in number keypad (the system keyboard is never shown). Wrong answers cost nothing but
 * time - the timer keeps running.
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
        callbacks.onTimerReset()
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
        testTag = "mission:math",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.mission_math_question, round + 1, mission.rounds),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MissionCard(
                modifier = Modifier.offset(x = rememberShakeOffset(attempts.takeIf { wrong } ?: 0)),
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
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
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
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
                AnswerDisplay(input)
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
            NumberKeypad(
                onDigit = { digit ->
                    if (input.length < MAX_INPUT_LENGTH) input += digit
                    callbacks.onTimerReset()
                },
                onBackspace = {
                    input = input.dropLast(1)
                    callbacks.onTimerReset()
                },
                onEnter = { answer(input.toLongOrNull()) },
            )
        }
    }
}

private const val MAX_INPUT_LENGTH = 8
private val KeyHeight = 64.dp

@Composable
private fun AnswerDisplay(input: String) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                .padding(horizontal = Spacing.l, vertical = Spacing.s)
                .semantics(mergeDescendants = true) {}
                .testTag("mission:math:input"),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (input.isEmpty()) {
            Text(
                text = stringResource(R.string.mission_math_answer_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = input,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun NumberKeypad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onEnter: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val digitRows = listOf("123", "456", "789")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        digitRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { digit ->
                    DigitKey(
                        digit = digit,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            onDigit(digit)
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    onBackspace()
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).height(KeyHeight).testTag("mission:math:backspace"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = stringResource(R.string.mission_math_backspace),
                )
            }
            DigitKey(
                digit = '0',
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    onDigit('0')
                },
            )
            Button(
                onClick = onEnter,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).height(KeyHeight).testTag("mission:math:enter"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardReturn,
                    contentDescription = stringResource(R.string.mission_math_submit),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun RowScope.DigitKey(digit: Char, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.weight(1f).height(KeyHeight).testTag("mission:math:key:$digit"),
    ) {
        Text(text = digit.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}
