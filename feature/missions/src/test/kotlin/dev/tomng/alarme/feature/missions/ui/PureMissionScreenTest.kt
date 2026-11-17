package dev.tomng.alarme.feature.missions.ui

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.TypingSentence
import dev.tomng.alarme.core.model.mission.TypingSentenceType
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.engine.MathMissionEngine
import dev.tomng.alarme.feature.missions.engine.MemoryMissionEngine
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class RecordingCallbacks : MissionStageCallbacks {
    var completed = 0
    var timerResets = 0
    var timerStops = 0

    override fun onComplete() {
        completed++
    }

    override fun onTimerReset() {
        timerResets++
    }

    override fun onTimerStop() {
        timerStops++
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MathMissionScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `tapping the correct multiple choice option completes the mission`() {
        val callbacks = RecordingCallbacks()
        // The screen draws its first question from a fresh engine; mirror that here.
        val question = MathMissionEngine(Random(5)).nextQuestion(0)
        val answer = question.answer
        rule.setContent {
            MathMissionScreen(
                Mission.Math(rounds = 1, difficulty = 0),
                callbacks,
                MathMissionEngine(Random(5)),
            )
        }

        rule
            .onAllNodesWithTag("mission:math:option")[question.options!!.indexOf(answer)]
            .performClick()

        assertThat(callbacks.completed).isEqualTo(1)
    }

    @Test
    fun `a wrong option shows the retry hint without completing`() {
        val callbacks = RecordingCallbacks()
        val question = MathMissionEngine(Random(5)).nextQuestion(0)
        val wrongIndex = question.options!!.indexOfFirst { it != question.answer }
        rule.setContent {
            MathMissionScreen(
                Mission.Math(rounds = 1, difficulty = 0),
                callbacks,
                MathMissionEngine(Random(5)),
            )
        }

        rule.onAllNodesWithTag("mission:math:option")[wrongIndex].performClick()

        assertThat(callbacks.completed).isEqualTo(0)
        rule.onNodeWithText("Not quite — try again").assertExists()
    }

    @Test
    fun `a typed answer completes a numeric round`() {
        val callbacks = RecordingCallbacks()
        val question = MathMissionEngine(Random(11)).nextQuestion(2)
        rule.setContent {
            MathMissionScreen(
                Mission.Math(rounds = 1, difficulty = 2),
                callbacks,
                MathMissionEngine(Random(11)),
            )
        }

        rule.onNodeWithTag("mission:math:input").performTextInput(question.answer.toString())
        rule.onNodeWithTag("mission:math:submit").performClick()

        assertThat(callbacks.completed).isEqualTo(1)
    }

    @Test
    fun `the numeric answer field is focused when shown`() {
        rule.setContent {
            MathMissionScreen(
                Mission.Math(rounds = 1, difficulty = 2),
                RecordingCallbacks(),
                MathMissionEngine(Random(11)),
            )
        }

        rule.onNodeWithTag("mission:math:input").assertIsFocused()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MemoryMissionScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `finding every highlighted tile completes the round and the mission`() {
        val callbacks = RecordingCallbacks()
        rule.setContent {
            MemoryMissionScreen(
                Mission.Memory(rounds = 1, difficulty = 3),
                callbacks,
                MemoryMissionEngine(Random(21)),
            )
        }

        // Let the memorize flash finish.
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()

        // Tapping every tile once hits the correct set; latched wrong tiles are inert.
        repeat(9) { index -> rule.onAllNodesWithTag("mission:memory:tile")[index].performClick() }
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()

        assertThat(callbacks.completed).isEqualTo(1)
    }

    @Test
    fun `a wrong tile shows as a mistake without completing`() {
        val callbacks = RecordingCallbacks()
        rule.setContent {
            MemoryMissionScreen(
                Mission.Memory(rounds = 1, difficulty = 3),
                callbacks,
                MemoryMissionEngine(Random(21)),
            )
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()

        val before = rule.onAllNodesWithTag("mission:memory:tile").fetchSemanticsNodes().size
        assertThat(before).isEqualTo(9)
        assertThat(callbacks.completed).isEqualTo(0)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TypingMissionScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun mission(rounds: Int = 1, text: String = "wake up now") =
        Mission.Typing(
            rounds = rounds,
            typingSentences = listOf(TypingSentence(TypingSentenceType.SHORT, text)),
        )

    @Test
    fun `typing the sentence exactly enables and completes the round`() {
        val callbacks = RecordingCallbacks()
        rule.setContent { TypingMissionScreen(mission(), callbacks) }

        rule.onNodeWithTag("mission:typing:submit").assertIsNotEnabled()
        rule.onNodeWithTag("mission:typing:input").performTextInput("wake up now")
        rule.onNodeWithTag("mission:typing:submit").performClick()

        assertThat(callbacks.completed).isEqualTo(1)
    }

    @Test
    fun `the input field is focused when shown`() {
        rule.setContent { TypingMissionScreen(mission(), RecordingCallbacks()) }

        rule.onNodeWithTag("mission:typing:input").assertIsFocused()
    }

    @Test
    fun `every keystroke resets the mission timer`() {
        val callbacks = RecordingCallbacks()
        rule.setContent { TypingMissionScreen(mission(text = "abc"), callbacks) }

        rule.onNodeWithTag("mission:typing:input").performTextInput("ab")

        assertThat(callbacks.timerResets).isAtLeast(1)
        assertThat(callbacks.completed).isEqualTo(0)
    }
}
