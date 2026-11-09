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
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

/** A one-off message for the list (denials, quick-alarm edit attempts, failed arming). */
enum class AlarmListMessage {
    SkipDisabled,
    SkipNotRepeating,
    QuickNotEditable,
    NotArmed,
}

/**
 * The alarm list: the alarms, the next one, and the actions the cards offer — toggle, skip once,
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

    val alarms: StateFlow<List<Alarm>> =
        alarmRepository
            .observeAlarms()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nextAlarm: StateFlow<Alarm?> =
        alarmRepository
            .observeNextAlarm()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<AlarmListMessage?>(null)
    val message: StateFlow<AlarmListMessage?> = _message.asStateFlow()

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            alarmRepository.setEnabled(alarm.id, enabled)
            rearm(
                if (enabled) RescheduleReason.TURN_ON_ALARM else RescheduleReason.TURN_OFF_ALARM,
            )
        }
    }

    fun setRingtone(alarm: Alarm, uri: String?) {
        viewModelScope.launch {
            alarmRepository.upsertAlarm(alarm.copy(ringtoneUri = uri, updatedAt = clock.now()))
            rearm(RescheduleReason.MODIFY_ALARM)
        }
    }

    fun skip(alarm: Alarm) {
        viewModelScope.launch {
            when (val result = skipAlarm.skip(alarm.id)) {
                SkipResult.Skipped -> rearm(RescheduleReason.PROCESS_SKIP)
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

    fun undoSkip(alarm: Alarm) {
        viewModelScope.launch {
            skipAlarm.undoSkip(alarm.id)
            rearm(RescheduleReason.PROCESS_SKIP)
        }
    }

    fun delete(alarm: Alarm) {
        viewModelScope.launch {
            alarmRepository.deleteAlarm(alarm.id)
            rearm(RescheduleReason.DELETE_ALARM)
        }
    }

    /** Quick alarms are one-shot "in X hours" alarms and cannot be edited afterwards. */
    fun createQuick(hours: Int) {
        viewModelScope.launch {
            val at = clock.now() + hours.hours
            val local = at.toLocalDateTime(clock.zone())
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
            rearm(RescheduleReason.ADD_ALARM)
        }
    }

    /**
     * Re-arms and reports when the platform refused to schedule the resulting alarm, so the list
     * can say so instead of showing an alarm that will silently never ring.
     */
    private suspend fun rearm(reason: RescheduleReason) {
        val result = scheduler.rearm(reason)
        if (result.next != null && !result.armed) {
            _message.value = AlarmListMessage.NotArmed
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
