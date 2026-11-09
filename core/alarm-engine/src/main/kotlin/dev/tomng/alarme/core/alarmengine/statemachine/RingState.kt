package dev.tomng.alarme.core.alarmengine.statemachine

import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.mission.Mission as AlarmMission
import dev.tomng.alarme.core.model.scheduling.ScheduleId
import kotlin.time.Duration
import kotlin.time.Instant

/** How the ring session was triggered; mirrored into the state for logging/restore decisions. */
enum class RingTrigger {
    /** Normal scheduled delivery. */
    SCHEDULED,

    /** The alarm manager re-delivered after process death — restore, don't re-log. */
    RESTORED,

    /** A missed-alarm grace re-delivery (delivery latency > grace window). */
    MISSED,

    /** Snooze finished and the alarm rings again. */
    SNOOZE,
}

/** Counters for the anti-mute policy: limit 3 by default, WordBeat exempt. */
data class MuteState(
    val count: Int,
    val limit: Int,
    val enabled: Boolean = true,
) {
    fun canMute(mission: AlarmMission?): Boolean =
        mission is AlarmMission.WordBeat || (enabled && count < limit)
}

/** Persisted snooze counters: count increments per snooze, limit comes from the alarm. */
data class SnoozeState(
    val count: Int,
    val limit: Int,
) {
    val exhausted: Boolean
        get() = limit >= 0 && count >= limit
}

/** The pure ring state tree: Idle / Ringing / WakeUpCheck / Pending. */
sealed interface RingState {
    data object Idle : RingState

    /** One ringing alarm with its counters and current stage. */
    data class Ringing(
        val alarm: Alarm,
        val scheduleId: ScheduleId,
        val trigger: RingTrigger,
        val ringEventId: Long,
        val startedAt: Instant,
        val autoDismiss: Duration?,
        val mute: MuteState,
        val snooze: SnoozeState,
        val stage: RingStage,
    ) : RingState

    /** The 100 s confirmation is running. */
    data class WakeUpCheck(
        val alarm: Alarm,
        val startTime: Instant,
    ) : RingState

    /** Dismissed, waiting for the check to fire; also the direct-boot restore path. */
    data class WakeUpCheckPending(
        val alarm: Alarm,
        val checkAt: Instant,
    ) : RingState
}

/** The stages within a ringing alarm. */
sealed interface RingStage {
    /** The alarm is loud and the dismiss/snooze buttons are live. */
    data object Ring : RingStage

    /** Squat/Step get a prepare screen so a physical mission can't start lying down. */
    data class PrepareMission(
        val index: Int,
        val mission: AlarmMission,
    ) : RingStage

    /** Mission at [index] is being attempted; [timer] is null for untimed missions. */
    data class Mission(
        val index: Int,
        val mission: AlarmMission,
        val timer: MissionTimer?,
    ) : RingStage

    /** Snoozed until [finishAt]; sound stopped, re-ring scheduled. */
    data class Snoozing(
        val finishAt: Instant,
    ) : RingStage

    /** Mission timed out, 40 s grace countdown. */
    data class Emergency(
        val index: Int,
        val mission: AlarmMission,
        val remaining: Duration,
    ) : RingStage
}

/** A per-mission countdown; [remaining] is updated by ticks, [total] powers the reset rule. */
data class MissionTimer(
    val remaining: Duration,
    val total: Duration,
)
