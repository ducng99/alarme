package dev.tomng.alarme.feature.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.MissionType
import kotlinx.datetime.DayOfWeek
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AlarmEditorScreenTest {

    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var saved = 0
    private var deleted = 0
    private var addedMission: MissionType? = null
    private var replacedMission: Mission? = null

    private fun setScreen(
        draft: AlarmDraft = AlarmDraft(),
        errors: List<DraftError> = emptyList(),
        soundTitle: String? = null,
    ) {
        compose.setContent {
            AlarmEditorScreen(
                state = AlarmEditorUiState(draft = draft, loading = false, errors = errors),
                soundTitle = soundTitle,
                onBack = { calls += "back" },
                callbacks =
                    AlarmEditorCallbacks(
                        onTime = { h, m -> calls += "time:$h:$m" },
                        onName = { calls += "name:$it" },
                        onEmoji = { calls += "emoji:$it" },
                        onToggleDay = { calls += "day:${it.name}" },
                        onEveryDay = { calls += "everyDay" },
                        onOneShot = { calls += "once" },
                        onEnabled = { calls += "enabled:$it" },
                        onVibrate = { calls += "vibrate:$it" },
                        onVolume = { calls += "volume:$it" },
                        onCrescendo = { calls += "crescendo:$it" },
                        onSnoozeEnabled = { calls += "snooze:$it" },
                        onWakeUpCheckMinutes = { calls += "wuc:$it" },
                        onMemo = { calls += "memo:$it" },
                        onTts = { calls += "tts:$it" },
                        onAddMission = {
                            addedMission = it
                            calls += "addMission:${it.name}"
                        },
                        onRemoveMission = { calls += "removeMission:$it" },
                        onReplaceMission = { _, mission ->
                            replacedMission = mission
                            calls += "replaceMission"
                        },
                        onSave = { saved++ },
                        onDelete = { deleted++ },
                    ),
            )
        }
    }

    private fun scrollToTag(tag: String) {
        compose.onNodeWithTag("editor_list").performScrollToNode(hasTestTag(tag))
    }

    private fun scrollToText(text: String) {
        compose.onNodeWithTag("editor_list").performScrollToNode(hasText(text))
    }

    @Test
    fun `a new alarm shows the new title and no delete action`() {
        setScreen()

        compose.onNodeWithText("New alarm").assertIsDisplayed()
        compose.onNodeWithTag("editor_delete").assertDoesNotExist()
    }

    @Test
    fun `an existing alarm shows the edit title and a delete action`() {
        setScreen(draft = AlarmDraft(id = 3, name = "Work"))

        compose.onNodeWithText("Edit alarm").assertIsDisplayed()
        compose.onNodeWithTag("editor_delete").performClick()

        assertThat(deleted).isEqualTo(1)
    }

    @Test
    fun `save and back are wired`() {
        setScreen()

        compose.onNodeWithTag("editor_save").performClick()
        compose.onNodeWithTag("editor_back").performClick()

        assertThat(saved).isEqualTo(1)
        assertThat(calls).contains("back")
    }

    @Test
    fun `the time row opens a picker that reports the chosen time`() {
        setScreen(draft = AlarmDraft(hour = 6, minute = 30))

        compose.onNodeWithTag("editor_time").performClick()
        compose.onNodeWithTag("editor_time_ok").performClick()

        assertThat(calls).contains("time:6:30")
    }

    @Test
    fun `the label field reports typing`() {
        setScreen()

        compose.onNodeWithTag("editor_name").performTextReplacement("Gym")

        assertThat(calls).contains("name:Gym")
    }

    @Test
    fun `day chips toggle and the presets are offered`() {
        setScreen(draft = AlarmDraft(days = setOf(DayOfWeek.MONDAY)))

        compose.onNodeWithTag("editor_day:WEDNESDAY").performClick()
        compose.onNodeWithTag("editor_every_day").performClick()
        compose.onNodeWithTag("editor_once").performClick()

        assertThat(calls).containsAtLeast("day:WEDNESDAY", "everyDay", "once")
    }

    @Test
    fun `the sound row shows the current sound`() {
        setScreen(draft = AlarmDraft(ringtoneUri = "file:///a.mp3"), soundTitle = "My song")

        scrollToText("My song")
        compose.onNodeWithText("My song").assertIsDisplayed()
        compose.onNodeWithTag("editor_sound").assertIsDisplayed()
    }

    @Test
    fun `the default sound label is used when no sound is set`() {
        setScreen()

        scrollToText("Default sound")
        compose.onNodeWithText("Default sound").assertIsDisplayed()
    }

    @Test
    fun `switches reflect the draft and report changes`() {
        setScreen(draft = AlarmDraft(vibrate = true, snoozeEnabled = false))

        scrollToTag("editor_vibrate")
        compose.onNodeWithTag("editor_vibrate").assertIsOn()
        compose.onNodeWithTag("editor_vibrate").performClick()

        scrollToTag("editor_snooze")
        compose.onNodeWithTag("editor_snooze").assertIsOff()
        compose.onNodeWithTag("editor_snooze").performClick()

        assertThat(calls).containsAtLeast("vibrate:false", "snooze:true")
    }

    @Test
    fun `snooze details are hidden when snooze is off`() {
        setScreen(draft = AlarmDraft(snoozeEnabled = false))

        scrollToTag("editor_snooze")
        compose.onNodeWithTag("editor_snooze_minutes:value").assertDoesNotExist()
    }

    @Test
    fun `snooze details appear when snooze is on`() {
        setScreen(draft = AlarmDraft(snoozeEnabled = true, snoozeMinutes = 5))

        scrollToTag("editor_snooze_minutes:value")
        compose.onNodeWithTag("editor_snooze_minutes:value").assertIsDisplayed()
    }

    @Test
    fun `the wake-up check toggle sets a default and can be tuned`() {
        setScreen(draft = AlarmDraft(wakeUpCheckMinutes = 0))

        scrollToTag("editor_wake_up_check")
        compose.onNodeWithTag("editor_wake_up_check").performClick()

        assertThat(calls).contains("wuc:5")
    }

    @Test
    fun `the memo field reports typing`() {
        setScreen()

        scrollToTag("editor_memo")
        compose.onNodeWithTag("editor_memo").performTextReplacement("up!")

        assertThat(calls).contains("memo:up!")
    }

    @Test
    fun `missions are listed with their summary and can be removed`() {
        setScreen(
            draft =
                AlarmDraft(
                    missions =
                        listOf(Mission.Math(rounds = 2, difficulty = 1), Mission.Shake(count = 20)),
                ),
        )

        scrollToText("1. Math")
        compose.onNodeWithText("1. Math").assertIsDisplayed()
        compose.onNodeWithText("2 rounds · level 1").assertIsDisplayed()
        compose.onNodeWithText("2. Shake").assertIsDisplayed()
        compose.onNodeWithText("20 times").assertIsDisplayed()

        scrollToTag("editor_mission_remove:1")
        compose.onNodeWithTag("editor_mission_remove:1").performClick()

        assertThat(calls).contains("removeMission:1")
    }

    @Test
    fun `an incomplete mission is flagged`() {
        setScreen(draft = AlarmDraft(missions = listOf(Mission.Barcode())))

        scrollToText("Needs setup")
        compose.onNodeWithText("Needs setup").assertIsDisplayed()
    }

    @Test
    fun `a mission can be added from the type list`() {
        setScreen()

        scrollToTag("editor_add_mission")
        compose.onNodeWithTag("editor_add_mission").performClick()
        scrollToTag("editor_mission_type:MEMORY")
        compose.onNodeWithTag("editor_mission_type:MEMORY").performClick()

        assertThat(addedMission).isEqualTo(MissionType.MEMORY)
    }

    @Test
    fun `adding is disabled at the mission cap`() {
        setScreen(draft = AlarmDraft(missions = List(5) { Mission.Math() }))

        scrollToTag("editor_add_mission")
        compose.onNodeWithTag("editor_add_mission").assertIsNotEnabled()
    }

    @Test
    fun `tapping a mission opens its configuration`() {
        setScreen(draft = AlarmDraft(missions = listOf(Mission.Math(rounds = 1, difficulty = 0))))

        scrollToTag("editor_mission:0")
        compose.onNodeWithTag("editor_mission:0").performClick()
        compose.onNodeWithTag("mission_config_rounds:plus").performClick()
        compose.onNodeWithTag("mission_config_ok").performClick()

        assertThat((replacedMission as Mission.Math).rounds).isEqualTo(2)
    }

    @Test
    fun `errors are shown`() {
        setScreen(errors = listOf(DraftError.IncompleteMission))

        scrollToText("Finish setting up your missions before saving.")
        compose.onNodeWithText("Finish setting up your missions before saving.").assertIsDisplayed()
    }
}
