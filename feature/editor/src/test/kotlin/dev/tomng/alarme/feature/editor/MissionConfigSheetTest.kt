package dev.tomng.alarme.feature.editor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.mission.ImageRecognitionObject
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.WordBeatLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MissionConfigSheetTest {

    @get:Rule val compose = createComposeRule()

    private var confirmed: Mission? = null
    private var dismissed = 0

    private fun setSheet(mission: Mission) {
        compose.setContent {
            MissionConfigSheet(
                mission = mission,
                onDismiss = { dismissed++ },
                onConfirm = { confirmed = it },
            )
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag("mission_config_scroll").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }

    private fun slideTo(tag: String, value: Float) {
        compose.onNodeWithTag("mission_config_scroll").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) {
            it(value)
        }
    }

    private fun clickButton(tag: String) {
        compose.onNodeWithTag(tag).performClick()
    }

    @Test
    fun `math difficulty is picked with the slider`() {
        setSheet(Mission.Math(difficulty = 0))

        slideTo("mission_config_difficulty", 4f)
        compose.onNodeWithText("Level 4").assertExists()
        clickButton("mission_config_ok")

        assertThat((confirmed as Mission.Math).difficulty).isEqualTo(4)
    }

    @Test
    fun `math difficulty shows whether answers are typed`() {
        setSheet(Mission.Math(difficulty = 0))

        compose.onNodeWithText("Multiple choice", substring = true).assertExists()
        slideTo("mission_config_difficulty", 2f)
        compose.onNodeWithText("Type the answer on a keypad", substring = true).assertExists()
    }

    @Test
    fun `memory difficulty describes the grid size`() {
        setSheet(Mission.Memory(difficulty = 3))

        slideTo("mission_config_difficulty", 5f)

        compose.onNodeWithText("5 × 5 grid").assertExists()
    }

    @Test
    fun `time limit is picked from presets`() {
        setSheet(Mission.Shake(timeLimitSeconds = 0))

        click("mission_config_time_limit:60")
        clickButton("mission_config_ok")

        assertThat(confirmed!!.timeLimitSeconds).isEqualTo(60)
    }

    @Test
    fun `a non-preset time limit is still offered and selected`() {
        setSheet(Mission.Shake(timeLimitSeconds = 90))

        compose
            .onNodeWithTag("mission_config_scroll")
            .performScrollToNode(hasTestTag("mission_config_time_limit:90"))
        compose.onNodeWithTag("mission_config_time_limit:90").assertIsSelected()
    }

    @Test
    fun `count presets set the count`() {
        setSheet(Mission.Step(count = 10))

        click("mission_config_count_preset:50")
        click("mission_config_count:plus")
        clickButton("mission_config_ok")

        assertThat((confirmed as Mission.Step).count).isEqualTo(55)
    }

    @Test
    fun `image objects toggle and show the selection count`() {
        setSheet(Mission.ImageRecognition(objects = listOf(ImageRecognitionObject.SINK)))

        click("mission_config_object:CAT")
        compose.onNodeWithText("2 selected").assertExists()
        clickButton("mission_config_ok")

        assertThat((confirmed as Mission.ImageRecognition).objects)
            .containsExactly(ImageRecognitionObject.SINK, ImageRecognitionObject.CAT)
    }

    @Test
    fun `word beat language resets the round sets`() {
        setSheet(MissionDefaults.of(dev.tomng.alarme.core.model.mission.MissionType.WORD_BEAT))

        click("mission_config_language:KOREAN")
        clickButton("mission_config_ok")

        val result = confirmed as Mission.WordBeat
        assertThat(result.language).isEqualTo(WordBeatLanguage.KOREAN)
        assertThat(result.selectedRoundSetIds)
            .isEqualTo(MissionDefaults.roundSetIds(WordBeatLanguage.KOREAN))
    }

    @Test
    fun `custom sentences can be typed across lines`() {
        setSheet(MissionDefaults.of(dev.tomng.alarme.core.model.mission.MissionType.TYPING))

        click("mission_config_sentence:CUSTOM")
        compose.onNodeWithTag("mission_config_custom_sentences").performTextInput("Wake up\n")
        compose.onNodeWithTag("mission_config_custom_sentences").performTextInput("Go")
        clickButton("mission_config_ok")

        assertThat((confirmed as Mission.Typing).typingSentences.map { it.sentence })
            .containsExactly("Wake up", "Go")
            .inOrder()
    }

    @Test
    fun `cancel discards the edits`() {
        setSheet(Mission.Math(rounds = 1))

        click("mission_config_rounds:plus")
        clickButton("mission_config_cancel")

        assertThat(dismissed).isEqualTo(1)
        assertThat(confirmed).isNull()
    }
}
