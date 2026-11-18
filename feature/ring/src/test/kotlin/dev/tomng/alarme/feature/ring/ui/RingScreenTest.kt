package dev.tomng.alarme.feature.ring.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.statemachine.MissionTimer
import dev.tomng.alarme.core.alarmengine.statemachine.RingStage
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.SnoozeOption
import dev.tomng.alarme.core.model.mission.Mission
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One screen state per stage of the ringing session, plus which inputs are live in each. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class RingScreenTest {

    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-10T06:00:00Z")

    @Before
    fun pinZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private fun alarm(
        missions: List<Mission> = emptyList(),
        snooze: SnoozeOption = SnoozeOption(),
    ) =
        Alarm(
            id = 7L,
            name = "Work",
            hour = 6,
            minute = 0,
            missions = missions,
            snoozeOption = snooze,
            createdAt = now,
            updatedAt = now,
        )

    private fun ringing(
        alarm: Alarm = alarm(),
        stage: RingStage = RingStage.Ring,
        snoozeCount: Int = 0,
    ) =
        RingState.Ringing(
            alarm = alarm,
            scheduleId =
                dev.tomng.alarme.core.model.scheduling.ScheduleId(
                    "d",
                    7L,
                    "20261110_0600",
                    "20261109_2200",
                ),
            trigger = dev.tomng.alarme.core.alarmengine.statemachine.RingTrigger.SCHEDULED,
            ringEventId = 11L,
            startedAt = now,
            autoDismiss = null,
            mute =
                dev.tomng.alarme.core.alarmengine.statemachine.MuteState(
                    count = 0,
                    limit = 3,
                    enabled = true,
                ),
            snooze =
                dev.tomng.alarme.core.alarmengine.statemachine.SnoozeState(
                    count = snoozeCount,
                    limit = alarm.snoozeOption.limit,
                ),
            stage = stage,
        )

    private fun mutSetOf(): RecordingCallbacks {
        val recorded = mutableListOf<String>()
        return RecordingCallbacks(
            recorded = recorded,
            dismissAction = { recorded += "dismiss" },
            snoozeAction = { recorded += "snooze" },
            missionStartAction = { recorded += "missionStart" },
            missionCompleteAction = { recorded += "missionComplete" },
            missionTimerResetAction = { recorded += "missionTimerReset" },
            emergencyAction = { recorded += "emergency" },
            emergencyTapAction = { recorded += "emergencyTap" },
            emergencyCancelAction = { recorded += "emergencyCancel" },
            wakeUpCheckCompleteAction = { recorded += "wakeUpCheckComplete" },
        )
    }

    @Test
    fun `the ring stage shows the time the label and both ways out`() {
        val cb = mutSetOf()
        compose.setContent { RingScreen(state = ringing(), callbacks = cb, nowProvider = { now }) }

        compose.onNodeWithText("Work").assertIsDisplayed()
        compose.onNodeWithText("6:00 AM").assertIsDisplayed()
        compose.onNodeWithTag("ring:dismiss").assertIsDisplayed()
        compose.onNodeWithText("Slide to dismiss").assertIsDisplayed()
        compose.onNodeWithText("Snooze 5 min").assertIsDisplayed()
        compose.onNodeWithText("3 left").assertIsDisplayed()
    }

    @Test
    fun `dismiss and snooze route to their events`() {
        val cb = mutSetOf()
        compose.setContent { RingScreen(state = ringing(), callbacks = cb, nowProvider = { now }) }

        compose.onNodeWithTag("ring:dismiss").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("ring:snooze").performClick()

        assertThat(cb.recorded).containsExactly("dismiss", "snooze").inOrder()
    }

    @Test
    fun `sliding all the way dismisses`() {
        val cb = mutSetOf()
        compose.setContent { RingScreen(state = ringing(), callbacks = cb, nowProvider = { now }) }

        compose.onNodeWithTag("ring:dismiss").performTouchInput {
            swipeRight(startX = left + 80f, endX = right)
        }
        compose.waitForIdle()

        assertThat(cb.recorded).containsExactly("dismiss")
    }

    @Test
    fun `a short slide springs back without dismissing`() {
        val cb = mutSetOf()
        compose.setContent { RingScreen(state = ringing(), callbacks = cb, nowProvider = { now }) }

        compose.onNodeWithTag("ring:dismiss").performTouchInput {
            swipeRight(startX = left + 80f, endX = left + width * 0.3f)
        }
        compose.waitForIdle()

        assertThat(cb.recorded).isEmpty()
    }

    @Test
    fun `the slide completes at seventy percent of its travel`() {
        assertThat(slideCompleted(offset = 69f, travel = 100f)).isFalse()
        assertThat(slideCompleted(offset = 70f, travel = 100f)).isTrue()
        assertThat(slideCompleted(offset = 0f, travel = 0f)).isFalse()
    }

    @Test
    fun `unlimited snoozes do not count down`() {
        val cb = mutSetOf()
        compose.setContent {
            RingScreen(
                state =
                    ringing(
                        alarm =
                            alarm(
                                snooze = SnoozeOption(minutes = 10, limit = SnoozeOption.UNLIMITED),
                            ),
                    ),
                callbacks = cb,
                nowProvider = { now },
            )
        }

        compose.onNodeWithText("Snooze 10 min").assertIsDisplayed()
        compose.onNodeWithText("left", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the snooze button disappears once the limit is reached`() {
        val cb = mutSetOf()
        compose.setContent {
            RingScreen(state = ringing(snoozeCount = 3), callbacks = cb, nowProvider = { now })
        }

        compose.onNodeWithTag("ring:dismiss").assertIsDisplayed()
        compose.onNodeWithTag("ring:snooze").assertDoesNotExist()
    }

    @Test
    fun `the snooze button disappears when the alarm forbids snoozing`() {
        val cb = mutSetOf()
        compose.setContent {
            RingScreen(
                state = ringing(alarm = alarm(snooze = SnoozeOption(enabled = false))),
                callbacks = cb,
                nowProvider = { now },
            )
        }

        compose.onNodeWithTag("ring:snooze").assertDoesNotExist()
    }

    @Test
    fun `the prepare stage shows the next mission and a start button`() {
        val cb = mutSetOf()
        val mission = Mission.Step(timeLimitSeconds = 30, count = 10)
        compose.setContent {
            RingScreen(
                state =
                    ringing(
                        alarm = alarm(missions = listOf(mission)),
                        stage = RingStage.PrepareMission(0, mission),
                    ),
                callbacks = cb,
            )
        }

        compose.onNodeWithText("Get ready").assertIsDisplayed()
        compose.onNodeWithText("Walk").assertIsDisplayed()
        compose.onNodeWithText("Start").performClick()

        assertThat(cb.recorded).containsExactly("missionStart")
    }

    @Test
    fun `the mission stage shows the countdown and the mission screen`() {
        val cb = mutSetOf()
        val mission =
            Mission.Typing(
                timeLimitSeconds = 30,
                rounds = 1,
                typingSentences =
                    listOf(
                        dev.tomng.alarme.core.model.mission.TypingSentence(
                            dev.tomng.alarme.core.model.mission.TypingSentenceType.SHORT,
                            "abc",
                        ),
                    ),
            )
        compose.setContent {
            RingScreen(
                state =
                    ringing(
                        alarm = alarm(missions = listOf(mission)),
                        stage = RingStage.Mission(0, mission, MissionTimer(30.seconds, 30.seconds)),
                    ),
                callbacks = cb,
            )
        }

        compose.onNodeWithText("0:30").assertIsDisplayed()
        compose.onNodeWithText("Mission 1 of 1").assertIsDisplayed()
        compose.onNodeWithTag("mission:typing").assertExists()

        compose.onNodeWithTag("mission:typing:input").performTextInput("abc")
        compose.onNodeWithTag("mission:typing:submit").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertThat(cb.recorded).containsExactly("missionTimerReset", "missionComplete").inOrder()
    }

    @Test
    fun `the mission stage offers the emergency way out`() {
        val cb = mutSetOf()
        val mission = Mission.Typing()
        compose.setContent {
            RingScreen(
                state =
                    ringing(
                        alarm = alarm(missions = listOf(mission)),
                        stage = RingStage.Mission(0, mission, MissionTimer(60.seconds, 60.seconds)),
                    ),
                callbacks = cb,
            )
        }

        compose.onNodeWithText("1:00").assertIsDisplayed()
        compose.onNodeWithTag("ring:emergency").performClick()

        assertThat(cb.recorded).containsExactly("emergency")
    }

    @Test
    fun `the prepare stage offers the emergency way out`() {
        val cb = mutSetOf()
        val mission = Mission.Squat(count = 10)
        compose.setContent {
            RingScreen(
                state =
                    ringing(
                        alarm = alarm(missions = listOf(mission)),
                        stage = RingStage.PrepareMission(0, mission),
                    ),
                callbacks = cb,
            )
        }

        compose.onNodeWithTag("ring:emergency").performClick()

        assertThat(cb.recorded).containsExactly("emergency")
    }

    @Test
    fun `the wake-up check stage shows the window countdown and the confirm button`() {
        val cb = mutSetOf()
        compose.setContent {
            RingScreen(
                state = RingState.WakeUpCheck(alarm(), startTime = now - 30.seconds),
                callbacks = cb,
                nowProvider = { now },
            )
        }

        compose.onNodeWithText("Wake Up Check").assertIsDisplayed()
        compose.onNodeWithText("1:10").assertIsDisplayed()
        compose
            .onNodeWithText("Confirm you're awake, or your alarm will ring again.")
            .assertIsDisplayed()

        compose.onNodeWithText("Yes, I'm up!").performClick()

        assertThat(cb.recorded).containsExactly("wakeUpCheckComplete")
    }

    @Test
    fun `an exhausted wake-up check window shows zero`() {
        compose.setContent {
            RingScreen(
                state = RingState.WakeUpCheck(alarm(), startTime = now - 101.seconds),
                callbacks = mutSetOf(),
                nowProvider = { now },
            )
        }

        compose.onNodeWithText("0:00").assertIsDisplayed()
    }

    @Test
    fun `the snoozing stage shows the resume time and a way out`() {
        val cb = mutSetOf()
        compose.setContent {
            RingScreen(
                state = ringing(stage = RingStage.Snoozing(now + 305.seconds)),
                callbacks = cb,
            )
        }

        compose.onNodeWithText("Snoozed until 6:05 AM").assertIsDisplayed()
        compose.onNodeWithTag("ring:dismiss").performClick()

        assertThat(cb.recorded).containsExactly("dismiss")
    }

    @Test
    fun `the snoozing stage of a mission alarm offers the missions instead of a plain dismiss`() {
        val cb = mutSetOf()
        compose.setContent {
            RingScreen(
                state =
                    ringing(
                        alarm = alarm(missions = listOf(Mission.Math())),
                        stage = RingStage.Snoozing(now + 305.seconds),
                    ),
                callbacks = cb,
            )
        }

        compose.onNodeWithText("Start missions").assertIsDisplayed()
        compose.onNodeWithTag("ring:dismiss").performClick()

        assertThat(cb.recorded).containsExactly("dismiss")
    }

    @Test
    fun `the emergency stage counts the remaining taps and records each tap`() {
        val cb = mutSetOf()
        val mission = Mission.Math(timeLimitSeconds = 30)
        compose.setContent {
            RingScreen(
                state =
                    ringing(
                        alarm = alarm(missions = listOf(mission)),
                        stage = RingStage.Emergency(0, mission, taps = 58),
                    ),
                callbacks = cb,
            )
        }

        compose.onNodeWithText("Emergency").assertIsDisplayed()
        compose.onNodeWithText("42").assertIsDisplayed()
        compose.onNodeWithTag("ring:emergency_tap").performClick()
        compose.onNodeWithTag("ring:emergency_tap").performClick()
        compose.onNodeWithTag("ring:emergency_cancel").performClick()

        assertThat(cb.recorded)
            .containsExactly("emergencyTap", "emergencyTap", "emergencyCancel")
            .inOrder()
    }

    @Test
    fun `idle renders nothing`() {
        compose.setContent { RingScreen(state = RingState.Idle, callbacks = mutSetOf()) }

        compose.onNodeWithTag("ring:content").assertDoesNotExist()
    }
}

private class RecordingCallbacks(
    val recorded: MutableList<String>,
    private val dismissAction: () -> Unit,
    private val snoozeAction: () -> Unit,
    private val missionStartAction: () -> Unit,
    private val missionCompleteAction: () -> Unit,
    private val missionTimerResetAction: () -> Unit,
    private val emergencyAction: () -> Unit,
    private val emergencyTapAction: () -> Unit,
    private val emergencyCancelAction: () -> Unit,
    private val wakeUpCheckCompleteAction: () -> Unit,
) : RingScreenCallbacks {
    override fun onDismiss() = dismissAction()

    override fun onSnooze() = snoozeAction()

    override fun onMissionStart() = missionStartAction()

    override fun onMissionComplete() = missionCompleteAction()

    override fun onMissionTimerReset() = missionTimerResetAction()

    override fun onEmergency() = emergencyAction()

    override fun onEmergencyTap() = emergencyTapAction()

    override fun onEmergencyCancel() = emergencyCancelAction()

    override fun onWakeUpCheckComplete() = wakeUpCheckCompleteAction()
}
