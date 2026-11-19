package dev.tomng.alarme.core.alarmengine.statemachine

import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.scheduling.ScheduleId
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The event vocabulary. Events that can complete the ring carry `now: Instant` so the reducer stays
 * pure (no clock injection, no hidden time reads).
 */
sealed interface RingEvent {

    /** Common shape of the two ring-start events so the reducer builds the session once. */
    interface RingSessionEvent : RingEvent {
        val alarm: Alarm
        val scheduleId: ScheduleId
        val trigger: RingTrigger
        val ringEventId: Long
        val startedAt: Instant
        val autoDismiss: Duration?
        val snoozeCount: Int
        val muteDuringMission: Boolean
        val missionMuteBudget: Duration
    }

    /** A validated ring delivery; [snoozeCount] restores persisted counters. */
    data class AlarmRingRequest(
        override val alarm: Alarm,
        override val scheduleId: ScheduleId,
        override val trigger: RingTrigger,
        override val ringEventId: Long,
        override val startedAt: Instant,
        override val autoDismiss: Duration? = null,
        override val snoozeCount: Int = 0,
        override val muteDuringMission: Boolean = true,
        override val missionMuteBudget: Duration = RingStateMachine.DEFAULT_MISSION_MUTE_BUDGET,
    ) : RingEvent, RingSessionEvent

    /** The service came back (reboot/redelivery) and is restoring a live ring. */
    data class AlarmRingRestored(
        override val alarm: Alarm,
        override val scheduleId: ScheduleId,
        override val trigger: RingTrigger = RingTrigger.RESTORED,
        override val ringEventId: Long,
        override val startedAt: Instant,
        override val autoDismiss: Duration? = null,
        override val snoozeCount: Int = 0,
        override val muteDuringMission: Boolean = true,
        override val missionMuteBudget: Duration = RingStateMachine.DEFAULT_MISSION_MUTE_BUDGET,
    ) : RingEvent, RingSessionEvent

    data class OnDismissClick(val now: Instant) : RingEvent

    /** [duration] overrides the alarm's snooze minutes (e.g. the fixed 180 s WUC snooze). */
    data class OnSnoozeClick(
        val now: Instant,
        val duration: Duration? = null,
    ) : RingEvent

    /** Prepare stage done, the user is in position — start the physical mission. */
    data class OnMissionStart(val now: Instant) : RingEvent

    data class OnMissionComplete(val now: Instant) : RingEvent

    data class OnMissionTimeout(val now: Instant) : RingEvent

    data class OnMissionTimerTick(val remaining: Duration) : RingEvent

    /** User activity (keystroke, tap, sensor progress): don't time out mid-attempt. */
    data object OnMissionTimerReset : RingEvent

    /** The user gives up on the mission and opens the tap-to-dismiss emergency. */
    data class OnEmergencyClick(val now: Instant) : RingEvent

    data class OnEmergencyTap(val now: Instant) : RingEvent

    /** Back from the emergency to the mission it was opened from. */
    data class OnEmergencyCancel(val now: Instant) : RingEvent

    /** The mute-during-mission budget ran out: the ring comes back for the rest of the session. */
    data object MissionMuteExhausted : RingEvent

    data class AutoDismissTimeout(val now: Instant) : RingEvent

    /** The user pressed volume-down (or tried to); the mute policy decides. */
    data object VolumeMuteRequested : RingEvent

    data class WakeUpCheckStarted(
        val alarm: Alarm,
        val startTime: Instant,
    ) : RingEvent

    /** Direct-boot restore of a pending check. */
    data class WakeUpCheckRestored(
        val alarm: Alarm,
        val checkAt: Instant,
    ) : RingEvent

    data object OnWakeUpCheckComplete : RingEvent

    data object OnWakeUpCheckCancelled : RingEvent

    /** 100 s elapsed — "your alarm will start ringing again". */
    data object OnWakeUpCheckFailed : RingEvent

    /** Validation refused the delivery (`u90/f0.b`): invalid or expired. */
    data class RingBlocked(val reason: BlockedRingReason) : RingEvent

    /** Received schedule id ≠ expected (`u90/f0.a`). */
    data class FalseAlarmDetected(val scheduleId: ScheduleId) : RingEvent
}

enum class BlockedRingReason {
    INVALID_ALARM,
    EXPIRED_ALARM,
}
