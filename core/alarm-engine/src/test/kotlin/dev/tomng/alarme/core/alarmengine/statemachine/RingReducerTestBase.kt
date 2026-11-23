package dev.tomng.alarme.core.alarmengine.statemachine

import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.SnoozeOption
import dev.tomng.alarme.core.model.alarm.WakeUpCheck
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.scheduling.ScheduleId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Shared fixtures for the reducer suites: one alarm, one schedule id, one fixed clock. */
abstract class RingReducerTestBase {

    protected val now: Instant = Instant.parse("2026-11-10T06:00:00Z")

    protected val scheduleId: ScheduleId =
        ScheduleId(
            deviceId = "d",
            alarmId = 7L,
            alarmTime = "20261110_0600",
            nowTime = "20261109_2200",
        )

    protected fun alarm(
        missions: List<Mission> = emptyList(),
        snoozeEnabled: Boolean = true,
        snoozeMinutes: Int = 5,
        snoozeLimit: Int = 3,
        wucMinutes: Int = 0,
    ): Alarm =
        Alarm(
            id = 7L,
            name = "Work",
            hour = 6,
            minute = 0,
            missions = missions,
            snoozeOption =
                SnoozeOption(enabled = snoozeEnabled, minutes = snoozeMinutes, limit = snoozeLimit),
            wakeUpCheck = WakeUpCheck(minutes = wucMinutes),
            createdAt = now,
            updatedAt = now,
        )

    protected fun request(
        alarm: Alarm = alarm(),
        trigger: RingTrigger = RingTrigger.SCHEDULED,
        autoDismiss: Duration? = null,
        snoozeCount: Int = 0,
        muteDuringMission: Boolean = true,
    ): RingEvent.AlarmRingRequest =
        RingEvent.AlarmRingRequest(
            alarm = alarm,
            scheduleId = scheduleId,
            trigger = trigger,
            ringEventId = 11L,
            startedAt = now,
            autoDismiss = autoDismiss,
            snoozeCount = snoozeCount,
            muteDuringMission = muteDuringMission,
        )

    protected fun ringing(
        alarm: Alarm = alarm(),
        stage: RingStage = RingStage.Ring,
        autoDismiss: Duration? = null,
        snoozeCount: Int = 0,
        muteDuringMission: Boolean = true,
        missionMuteLeft: Int = RingStateMachine.DEFAULT_MISSION_MUTE_LIMIT,
    ): RingState.Ringing =
        RingState.Ringing(
            alarm = alarm,
            scheduleId = scheduleId,
            trigger = RingTrigger.SCHEDULED,
            ringEventId = 11L,
            startedAt = now,
            autoDismiss = autoDismiss,
            mute = MuteState(count = 0, limit = 3, enabled = true),
            snooze = SnoozeState(count = snoozeCount, limit = alarm.snoozeOption.limit),
            stage = stage,
            muteDuringMission = muteDuringMission,
            missionMuteLeft = missionMuteLeft,
            // A mission stage reached through the chain is silenced from the moment it started.
            silenced = muteDuringMission && stage is RingStage.Mission && missionMuteLeft > 0,
        )

    protected fun missionStage(index: Int, mission: Mission): RingStage.Mission =
        RingStage.Mission(index = index, mission = mission, timer = mission.timerOf())

    protected fun Mission.timerOf(): MissionTimer {
        val limit = if (timeLimitSeconds > 0) timeLimitSeconds.seconds else 60.seconds
        return MissionTimer(limit, limit)
    }

    protected fun reduce(state: RingState, event: RingEvent): RingTransition =
        RingStateMachine.reduce(state, event)
}
