package dev.tomng.alarme.core.alarmengine.statemachine

import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The effect (command) vocabulary. The reducer emits these; the effect executor (the only
 * Android-touching part) consumes them. Timers are commands, not threads: the executor arms them
 * and feeds tick/timeout events back into the machine.
 */
sealed interface RingEffect {

    /** Start the ring audio. */
    data class Ring(val alarm: Alarm) : RingEffect

    /** Restore a ring after process death without re-logging the ring event. */
    data class RestoreRing(val alarm: Alarm) : RingEffect

    /** DismissAlarm — stop everything, re-arm the next occurrence, log the outcome. */
    data class Dismiss(
        val alarm: Alarm,
        val reason: DismissReason,
    ) : RingEffect

    /** SnoozeAlarm — stop audio, re-ring at [finishAt], "snoozed until" notification. */
    data class Snooze(
        val alarm: Alarm,
        val finishAt: Instant,
        val duration: Duration,
        val count: Int,
    ) : RingEffect

    data object StopSnoozeTimer : RingEffect

    /** Leave the snooze early: cancel the pending snooze PI and ring again. */
    data class ResumeFromSnooze(val alarm: Alarm) : RingEffect

    /** ResetSnooze — zero the persisted snooze counters after a completed ring. */
    data object ResetSnooze : RingEffect

    data class StartAutoDismissTimer(val duration: Duration) : RingEffect

    data object StopAutoDismissTimer : RingEffect

    /** Arm the 100 s confirmation window; the executor feeds the failure back. */
    data class StartWakeUpCheckTimer(val duration: Duration) : RingEffect

    data object StopWakeUpCheckTimer : RingEffect

    /** Arm the mission timer; the executor feeds ticks back. */
    data class StartMissionTimer(
        val mission: Mission,
        val duration: Duration,
    ) : RingEffect

    data object StopMissionTimer : RingEffect

    /** Mute the ring sound while a mission is being solved; [ResumeRing] brings it back. */
    data object SilenceRing : RingEffect

    data object ResumeRing : RingEffect

    /** Volume-down honored — lower the forced volume (the audio layer ducks). */
    data object MuteAlarm : RingEffect

    /** Mute limit reached — re-force the volume (`MissionMaxMuteLimitExceeded`). */
    data object MuteDenied : RingEffect

    /** Arm the check at [at] (request code 100). */
    data class ScheduleWakeUpCheck(
        val alarm: Alarm,
        val at: Instant,
    ) : RingEffect

    data object CancelWakeUpCheck : RingEffect

    /**
     * A missed check explicitly re-rings instead of collapsing to Idle and relying on the service
     * to notice.
     */
    data class ReRing(val alarm: Alarm) : RingEffect

    /**
     * `ScheduleFor{Invalid,False,Expired}Alarm` — re-arm the next occurrence after a refused ring.
     */
    data class Reschedule(val reason: RescheduleReason) : RingEffect
}

/** Why the alarm ended. */
enum class DismissReason {
    NORMAL,
    AUTO,
    EMERGENCY,
}
