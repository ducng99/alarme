package dev.tomng.alarme.feature.alarmlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AlarmType
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import java.util.TimeZone
import kotlin.time.Instant
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The list's rendering: cards, badges, the next-alarm header, and the quick creator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AlarmListScreenTest {

    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-10T06:00:00Z")
    private val recorded = mutableListOf<String>()

    @Before
    fun pinZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private fun alarm(
        id: Long = 7,
        name: String = "Work",
        hour: Int = 7,
        minute: Int = 0,
        enabled: Boolean = true,
        days: DaysOfWeek = DaysOfWeek.EVERY_DAY,
        type: AlarmType = AlarmType.NORMAL,
        skipped: Boolean = false,
    ) =
        Alarm(
            id = id,
            type = type,
            name = name,
            hour = hour,
            minute = minute,
            enabled = enabled,
            daysOfWeek = days,
            isSkipped = skipped,
            createdAt = now,
            updatedAt = now,
        )

    private fun callbacks() =
        AlarmListCallbacks(
            onToggle = { alarm, enabled -> recorded += "toggle:${alarm.id}:$enabled" },
            onSkip = { recorded += "skip:${it.id}" },
            onUndoSkip = { recorded += "undo:${it.id}" },
            onDelete = { recorded += "delete:${it.id}" },
            onQuickCreate = { recorded += "quick:$it" },
            onPickSound = { recorded += "sound:${it.id}" },
            onPickDefault = { recorded += "default" },
            onOpenEditor = { recorded += "edit:$it" },
        )

    private fun setContent(alarms: List<Alarm>, next: Alarm? = alarms.firstOrNull()) {
        compose.setContent {
            AlarmListContent(alarms = alarms, nextAlarm = next, callbacks = callbacks())
        }
    }

    @Test
    fun `a card shows the time, name and days`() {
        setContent(listOf(alarm()))

        compose.onNodeWithText("7:00 AM").assertIsDisplayed()
        compose.onNodeWithTag("alarmlist:card:7").assertTextContains("Work · Daily")
    }

    @Test
    fun `the next alarm header shows the next ring time`() {
        setContent(listOf(alarm(hour = 6, minute = 30)))

        compose.onNodeWithText("Next alarm: 6:30 AM").assertIsDisplayed()
    }

    @Test
    fun `tapping a card opens the editor for that alarm`() {
        setContent(listOf(alarm(id = 7)))

        compose.onNodeWithTag("alarmlist:card:7").performClick()

        assertThat(recorded).containsExactly("edit:7")
    }

    @Test
    fun `the new alarm button opens a blank editor`() {
        setContent(emptyList(), next = null)

        compose.onNodeWithTag("alarmlist:new").performClick()

        assertThat(recorded).containsExactly("edit:0")
    }

    @Test
    fun `an empty list shows the empty state`() {
        setContent(emptyList(), next = null)

        compose.onNodeWithText("No alarms yet").assertIsDisplayed()
        compose.onNodeWithText("Create a quick alarm to get started.").assertIsDisplayed()
    }

    @Test
    fun `alarms that are all switched off say so instead of claiming there are none`() {
        setContent(listOf(alarm(enabled = false)), next = null)

        compose.onNodeWithText("No enabled alarms").assertIsDisplayed()
        compose.onNodeWithText("No alarms yet").assertDoesNotExist()
    }

    @Test
    fun `a skipped alarm shows the will-be-skipped badge and the undo action`() {
        setContent(listOf(alarm(skipped = true)))

        compose.onNodeWithTag("alarmlist:list").performScrollToNode(hasTestTag("alarmlist:card:7"))
        compose.onNodeWithTag("alarmlist:skipped:7", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Will be skipped", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("alarmlist:menu:7").performClick()
        compose.onNodeWithText("Undo skip").performClick()

        assertThat(recorded).containsExactly("undo:7")
    }

    @Test
    fun `a quick alarm shows its badge and a one-shot schedule`() {
        setContent(listOf(alarm(type = AlarmType.QUICK, days = DaysOfWeek.NONE)))

        compose.onNodeWithTag("alarmlist:list").performScrollToNode(hasTestTag("alarmlist:card:7"))
        compose.onNodeWithTag("alarmlist:card:7").assertTextContains("Quick alarm")
        compose.onNodeWithTag("alarmlist:card:7").assertTextContains("Once", substring = true)
    }

    @Test
    fun `the toggle reports the new enabled state`() {
        setContent(listOf(alarm()))

        compose.onNodeWithTag("alarmlist:toggle:7").assertIsOn().performClick()

        assertThat(recorded).containsExactly("toggle:7:false")
    }

    @Test
    fun `a disabled alarm renders its switch off`() {
        setContent(listOf(alarm(enabled = false)))

        compose.onNodeWithTag("alarmlist:toggle:7").assertIsOff()
    }

    @Test
    fun `skip once and delete route to their actions`() {
        setContent(listOf(alarm()))

        compose.onNodeWithTag("alarmlist:list").performScrollToNode(hasTestTag("alarmlist:card:7"))
        compose.onNodeWithTag("alarmlist:menu:7").performClick()
        compose.onNodeWithText("Skip once").performClick()
        compose.onNodeWithTag("alarmlist:menu:7").performClick()
        compose.onNodeWithText("Delete").performClick()

        assertThat(recorded).containsExactly("skip:7", "delete:7").inOrder()
    }

    @Test
    fun `the quick creator offers the hour chips`() {
        setContent(emptyList(), next = null)

        compose.onNodeWithTag("alarmlist:quick:1").performClick()
        compose.onNodeWithTag("alarmlist:quick:8").performClick()

        assertThat(recorded).containsExactly("quick:1", "quick:8").inOrder()
    }
}
