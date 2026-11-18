package dev.tomng.alarme.feature.editor

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    private var addedMission: Mission? = null
    private var replacedMission: Mission? = null

    private fun setScreen(
        draft: AlarmDraft = AlarmDraft(),
        errors: List<DraftError> = emptyList(),
        loading: Boolean = false,
        soundTitle: String? = null,
        backupSoundTitle: String? = null,
    ) {
        compose.setContent {
            AlarmEditorScreen(
                state = AlarmEditorUiState(draft = draft, loading = loading, errors = errors),
                soundTitle = soundTitle,
                backupSoundTitle = backupSoundTitle,
                onBack = { calls += "back" },
                callbacks =
                    AlarmEditorCallbacks(
                        onTime = { h, m -> calls += "time:$h:$m" },
                        onName = { calls += "name:$it" },
                        onEmoji = { calls += "emoji:$it" },
                        onToggleDay = { calls += "day:${it.name}" },
                        onDays = { calls += "days:${it.size}" },
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
                            calls += "addMission"
                        },
                        onRemoveMission = { calls += "removeMission:$it" },
                        onReplaceMission = { _, mission ->
                            replacedMission = mission
                            calls += "replaceMission"
                        },
                        onRestoreMission = { calls += "restoreMission" },
                        onMoveMission = { from, to -> calls += "move:$from:$to" },
                        onOpenSound = { calls += "openSound:$it" },
                        onOpenBackupSound = { calls += "openBackup:$it" },
                        onSnoozeMinutes = { calls += "snoozeMinutes:$it" },
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
    fun `the form stays hidden until the alarm has loaded`() {
        setScreen(loading = true)

        compose.onNodeWithTag("editor_hour").assertDoesNotExist()
        compose.onNodeWithTag("editor_save").assertDoesNotExist()
    }

    @Test
    fun `the emoji button shows the current emoji and the picker starts closed`() {
        setScreen(draft = AlarmDraft(emoji = "🌅"))

        compose.onNodeWithTag("editor_emoji").assertTextEquals("🌅")
        compose.onNodeWithTag("editor_emoji_picker").assertDoesNotExist()
    }

    @Test
    fun `tapping the emoji button opens the emoji picker without picking anything`() {
        setScreen()

        compose.onNodeWithTag("editor_emoji").performClick()

        compose.onNodeWithTag("editor_emoji_picker").assertIsDisplayed()
        assertThat(calls.filter { it.startsWith("emoji:") }).isEmpty()
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
    fun `the time wheels step the time without a dialog`() {
        setScreen(draft = AlarmDraft(hour = 6, minute = 30))

        compose.onNodeWithTag("editor_minute").assertContentDescriptionEquals("Minute")
        compose.onNodeWithTag("editor_minute").performCustomAction("Increase")
        compose.onNodeWithTag("editor_hour").performCustomAction("Decrease")

        assertThat(calls).containsAtLeast("time:6:31", "time:5:30")
    }

    @Test
    fun `the time can be typed instead`() {
        setScreen(draft = AlarmDraft(hour = 6, minute = 30))

        compose.onNodeWithTag("editor_time_mode").performClick()

        compose.onNodeWithTag("editor_time_input").assertIsDisplayed()
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
        compose.onNodeWithTag("editor_weekdays").performClick()
        compose.onNodeWithTag("editor_once").performClick()

        assertThat(calls).containsAtLeast("day:WEDNESDAY", "days:7", "days:5", "days:0")
    }

    @Test
    fun `the matching preset is shown as selected`() {
        setScreen(draft = AlarmDraft(days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))

        compose.onNodeWithTag("editor_weekends").assertIsOn()
        compose.onNodeWithTag("editor_once").assertIsOff()
    }

    @Test
    fun `the sound row shows the current sound`() {
        setScreen(draft = AlarmDraft(ringtoneUri = "file:///a.mp3"), soundTitle = "My song")

        scrollToText("My song")
        compose.onNodeWithText("My song").assertIsDisplayed()
        compose.onNodeWithTag("editor_sound").performClick()

        assertThat(calls).contains("openSound:file:///a.mp3")
    }

    @Test
    fun `the default sound label is used when no sound is set`() {
        setScreen()

        scrollToText("Default sound")
        compose.onNodeWithText("Default sound").assertIsDisplayed()
    }

    @Test
    fun `the backup sound row appears only when backup sound is on`() {
        setScreen(draft = AlarmDraft(backupSoundEnabled = false))

        scrollToTag("editor_backup_sound")
        compose.onNodeWithTag("editor_backup_sound_pick").assertDoesNotExist()
    }

    @Test
    fun `the backup sound row shows the chosen sound`() {
        setScreen(
            draft = AlarmDraft(backupSoundEnabled = true, backupSoundUri = "file:///loud.mp3"),
            backupSoundTitle = "Siren",
        )

        scrollToTag("editor_backup_sound_pick")
        compose.onNodeWithText("Siren").assertIsDisplayed()
    }

    @Test
    fun `the backup sound row falls back to the system alarm label`() {
        setScreen(draft = AlarmDraft(backupSoundEnabled = true))

        scrollToTag("editor_backup_sound_pick")
        compose.onNodeWithText("System alarm sound").assertIsDisplayed()
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

        scrollToTag("editor_snooze_minutes")
        compose
            .onNodeWithTag("editor_snooze_minutes:value", useUnmergedTree = true)
            .assertTextEquals("5 min")
    }

    @Test
    fun `the snooze length is picked on a wheel`() {
        setScreen(draft = AlarmDraft(snoozeEnabled = true, snoozeMinutes = 5))

        scrollToTag("editor_snooze_minutes")
        compose.onNodeWithTag("editor_snooze_minutes").performClick()
        compose.onNodeWithTag("editor_snooze_minutes:sheet:wheel").performCustomAction("Increase")
        compose.onNodeWithTag("editor_snooze_minutes:sheet:ok").performClick()
        compose.waitForIdle()

        assertThat(calls).contains("snoozeMinutes:6")
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
        compose.onNodeWithText("Undo").performClick()

        assertThat(calls).containsAtLeast("removeMission:1", "restoreMission").inOrder()
    }

    @Test
    fun `missions can be moved`() {
        setScreen(draft = AlarmDraft(missions = listOf(Mission.Math(), Mission.Shake())))

        scrollToTag("editor_mission_move:1")
        compose.onNodeWithTag("editor_mission_move:1").performClick()

        assertThat(calls).contains("move:1:0")
    }

    @Test
    fun `an incomplete mission is flagged`() {
        setScreen(draft = AlarmDraft(missions = listOf(Mission.Barcode())))

        scrollToText("Needs setup")
        compose.onNodeWithText("Needs setup").assertIsDisplayed()
    }

    @Test
    fun `picking a mission type opens its configuration before adding it`() {
        setScreen()

        pickMissionType(MissionType.MEMORY)

        compose.onNodeWithTag("mission_config").assertIsDisplayed()
        assertThat(addedMission).isNull()

        compose.onNodeWithTag("mission_config_ok").performClick()

        assertThat(addedMission).isEqualTo(MissionDefaults.of(MissionType.MEMORY))
        compose.onNodeWithTag("mission_config").assertDoesNotExist()
    }

    @Test
    fun `cancelling the configuration of a new mission adds nothing`() {
        setScreen()

        pickMissionType(MissionType.SHAKE)
        compose.onNodeWithTag("mission_config_cancel").performClick()

        assertThat(addedMission).isNull()
        compose.onNodeWithTag("mission_config").assertDoesNotExist()
    }

    private fun pickMissionType(type: MissionType) {
        scrollToTag("editor_add_mission")
        compose.onNodeWithTag("editor_add_mission").performClick()
        compose.onNodeWithTag("editor_mission_type:${type.name}").performScrollTo().performClick()
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
    fun `errors are shown next to the missions`() {
        setScreen(
            draft = AlarmDraft(missions = listOf(Mission.Barcode())),
            errors = listOf(DraftError.IncompleteMission),
        )

        scrollToTag("editor_errors")
        compose.onNodeWithText("Finish setting up your missions before saving.").assertIsDisplayed()
    }
}

/** Runs the accessibility custom action labelled [label] on this node. */
internal fun androidx.compose.ui.test.SemanticsNodeInteraction.performCustomAction(label: String) {
    val actions =
        fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
    actions.single { it.label == label }.action()
}
