package dev.tomng.alarme.core.data.repository

import javax.inject.Inject

/** Why a skip was refused. */
enum class SkipDenial {
    /** "You can only skip enabled alarms". */
    Disabled,

    /** "You can only skip repeated alarms" — a one-shot alarm is turned off instead. */
    NotRepeating,

    /** The alarm no longer exists. */
    Missing,
}

sealed interface SkipResult {
    data object Skipped : SkipResult

    data class Denied(val reason: SkipDenial) : SkipResult
}

/**
 * Skip once: the next occurrence of a repeating alarm is dropped exactly once. The flag is consumed
 * by the ring that follows the skipped occurrence.
 */
class SkipAlarmUseCase
@Inject
constructor(
    private val alarmRepository: AlarmRepository,
) {
    suspend fun skip(alarmId: Long): SkipResult {
        val alarm =
            alarmRepository.getAlarm(alarmId) ?: return SkipResult.Denied(SkipDenial.Missing)
        if (!alarm.enabled) return SkipResult.Denied(SkipDenial.Disabled)
        if (!alarm.daysOfWeek.isRepeating) return SkipResult.Denied(SkipDenial.NotRepeating)
        alarmRepository.setSkipped(alarmId, true)
        return SkipResult.Skipped
    }

    suspend fun undoSkip(alarmId: Long) {
        alarmRepository.setSkipped(alarmId, false)
    }
}
