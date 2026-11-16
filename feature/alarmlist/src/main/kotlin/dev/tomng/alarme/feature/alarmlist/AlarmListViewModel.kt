package dev.tomng.alarme.feature.alarmlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.data.repository.AlarmRepository
import dev.tomng.alarme.core.data.repository.SkipAlarmUseCase
import dev.tomng.alarme.core.data.repository.SkipDenial
import dev.tomng.alarme.core.data.repository.SkipResult
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AlarmType
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import dev.tomng.alarme.core.model.scheduling.NextOccurrenceCalculator
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

/** A one-off message for the list: confirmations with undo, denials, and failed arming. */
sealed interface AlarmListMessage {
    data object SkipDisabled : AlarmListMessage

    data object SkipNotRepeating : AlarmListMessage

    data object QuickNotEditable : AlarmListMessage

    data object NotArmed : AlarmListMessage

    data object Deleted : AlarmListMessage

    data class Skipped(val alarmId: Long) : AlarmListMessage

    data class TurnedOn(val at: Instant) : AlarmListMessage

    data class QuickCreated(val at: Instant) : AlarmListMessage
}

/**
 * The alarm list: the alarms, the next one, and the actions the cards offer - toggle, skip once,
 * undo skip, delete, and the quick-alarm creator. Every mutation re-arms the scheduler.
 */
@HiltViewModel
class AlarmListViewModel
@Inject
constructor(
    private val alarmRepository: AlarmRepository,
    private val skipAlarm: SkipAlarmUseCase,
    private val scheduler: AlarmScheduler,
    private val clock: AppClock,
) : ViewModel() {

    /**
     * Enabled alarms first, each group by its next ring; every alarm carries that ring time. The
     * list is recomputed when the soonest ring passes, so a rung alarm moves on to its next day.
     * Null until the repository first answers.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val alarms: StateFlow<List<Alarm>?> =
        alarmRepository
            .observeAlarms()
            .flatMapLatest { stored -> listedUntilRingsPass(stored, clock) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val nextAlarm: StateFlow<Alarm?> =
        alarms
            .map { list -> list?.firstOrNull { it.enabled && it.nextOccurrence != null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<AlarmListMessage?>(null)
    val message: StateFlow<AlarmListMessage?> = _message.asStateFlow()

    private var lastDeleted: Alarm? = null
    private var lastQuickId: Long? = null

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            alarmRepository.setEnabled(alarm.id, enabled)
            val armed =
                rearm(
                    if (enabled) RescheduleReason.TURN_ON_ALARM
                    else RescheduleReason.TURN_OFF_ALARM,
                )
            if (enabled && armed) {
                nextRingOf(alarm.copy(enabled = true, isSkipped = false))?.let {
                    _message.value = AlarmListMessage.TurnedOn(it)
                }
            }
        }
    }

    fun setRingtone(alarm: Alarm, uri: String?) {
        viewModelScope.launch {
            val current = alarmRepository.getAlarm(alarm.id) ?: alarm
            alarmRepository.upsertAlarm(current.copy(ringtoneUri = uri, updatedAt = clock.now()))
            rearm(RescheduleReason.MODIFY_ALARM)
        }
    }

    fun skip(alarm: Alarm) {
        viewModelScope.launch {
            when (val result = skipAlarm.skip(alarm.id)) {
                SkipResult.Skipped -> {
                    _message.value = AlarmListMessage.Skipped(alarm.id)
                    rearm(RescheduleReason.PROCESS_SKIP)
                }
                is SkipResult.Denied ->
                    _message.value =
                        when (result.reason) {
                            SkipDenial.Disabled -> AlarmListMessage.SkipDisabled
                            SkipDenial.NotRepeating -> AlarmListMessage.SkipNotRepeating
                            SkipDenial.Missing -> null
                        }
            }
        }
    }

    fun undoSkip(alarm: Alarm) = undoSkip(alarm.id)

    fun undoSkip(alarmId: Long) {
        viewModelScope.launch {
            skipAlarm.undoSkip(alarmId)
            rearm(RescheduleReason.PROCESS_SKIP)
        }
    }

    fun delete(alarm: Alarm) {
        viewModelScope.launch {
            val full = alarmRepository.getAlarm(alarm.id) ?: alarm
            alarmRepository.deleteAlarm(alarm.id)
            lastDeleted = full
            _message.value = AlarmListMessage.Deleted
            rearm(RescheduleReason.DELETE_ALARM)
        }
    }

    /** Restores the alarm removed by the last [delete], while its undo is still on screen. */
    fun undoDelete() {
        val alarm = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            alarmRepository.upsertAlarm(alarm.copy(updatedAt = clock.now()))
            rearm(RescheduleReason.ADD_ALARM)
        }
    }

    /** Quick alarms are one-shot "in X minutes" alarms and cannot be edited afterwards. */
    fun createQuick(minutes: Int) {
        viewModelScope.launch {
            val at = clock.now() + minutes.minutes
            val local = at.toLocalDateTime(clock.zone())
            val id =
                alarmRepository.upsertAlarm(
                    Alarm(
                        id = Alarm.NEW_ID,
                        type = AlarmType.QUICK,
                        name = "",
                        hour = local.hour,
                        minute = local.minute,
                        daysOfWeek = DaysOfWeek.NONE,
                        enabled = true,
                        createdAt = clock.now(),
                        updatedAt = clock.now(),
                    ),
                )
            lastQuickId = id
            if (rearm(RescheduleReason.ADD_ALARM)) {
                _message.value = AlarmListMessage.QuickCreated(at.truncatedToMinute())
            }
        }
    }

    /** Removes the alarm made by the last [createQuick]. */
    fun undoQuick() {
        val id = lastQuickId ?: return
        lastQuickId = null
        viewModelScope.launch {
            alarmRepository.deleteAlarm(id)
            rearm(RescheduleReason.DELETE_ALARM)
        }
    }

    /**
     * Re-arms and reports when the platform refused to schedule the resulting alarm, so the list
     * can say so instead of showing an alarm that will silently never ring. Returns whether the
     * schedule is healthy.
     */
    private suspend fun rearm(reason: RescheduleReason): Boolean {
        val result = scheduler.rearm(reason)
        if (result.next != null && !result.armed) {
            _message.value = AlarmListMessage.NotArmed
            return false
        }
        return true
    }

    fun consumeMessage() {
        when (_message.value) {
            AlarmListMessage.Deleted -> lastDeleted = null
            is AlarmListMessage.QuickCreated -> lastQuickId = null
            else -> Unit
        }
        _message.value = null
    }

    private fun nextRingOf(alarm: Alarm): Instant? =
        NextOccurrenceCalculator.nextOccurrence(alarm, clock.now(), clock.zone())

    private fun Instant.truncatedToMinute(): Instant =
        Instant.fromEpochSeconds(epochSeconds - epochSeconds % SECONDS_PER_MINUTE)

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
