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
    ) : RingEvent, RingSessionEvent

    data class OnDismissClick(val now: Instant) : RingEvent

    /** [duration] overrides the alarm's snooze minutes (e.g. the fixed 180 s WUC snooze). */
    data class OnSnoozeClick(
        val now: Instant,
        val duration: Duration? = null,
    ) : RingEvent

    /** Prepare stage done, the user is in position — start the physical mission. */
    data object OnMissionStart : RingEvent

    data class OnMissionComplete(val now: Instant) : RingEvent

    data object OnMissionTimeout : RingEvent

    data class OnMissionTimerTick(val remaining: Duration) : RingEvent

    /** User activity (keystroke, confirming match): don't time out mid-attempt. */
    data object OnMissionTimerReset : RingEvent

    /** A match is being confirmed (ImageRecognition): pause the timer. */
    data object OnMissionTimerStop : RingEvent

    /** The +10 s mercy button. */
    data object OnAddMissionTimeClick : RingEvent

    data class OnEmergencyComplete(val now: Instant) : RingEvent

    data object OnEmergencyCancel : RingEvent

    data class OnEmergencyTimerTick(
        val remaining: Duration,
        val now: Instant,
    ) : RingEvent

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
