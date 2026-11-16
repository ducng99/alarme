package dev.tomng.alarme.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.data.repository.AlarmRepository
import dev.tomng.alarme.core.data.repository.RingtoneRepository
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.scheduling.NextOccurrenceCalculator
import javax.inject.Inject
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/** What the editor is doing right now. */
data class AlarmEditorUiState(
    val draft: AlarmDraft = AlarmDraft(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val errors: List<DraftError> = emptyList(),
    val finished: EditorOutcome? = null,
    /** The save went through but the platform refused to arm the alarm. */
    val notArmed: Boolean = false,
    /** The draft as loaded; edits are compared against it to warn before discarding. */
    val original: AlarmDraft = draft,
    /** When the draft would next ring if saved now, or null when it would not ring. */
    val nextRing: Instant? = null,
) {
    val hasChanges: Boolean
        get() = draft != original
}

/** Terminal events the host reacts to (navigate back, show a message). */
enum class EditorOutcome {
    Saved,
    Deleted,
}

/**
 * The alarm editor: loads an existing alarm (or starts a blank one), applies field edits to the
 * draft, and persists it — re-arming the scheduler on every successful write.
 */
@Suppress("TooManyFunctions") // one one-line setter per editable field
@HiltViewModel
class AlarmEditorViewModel
@Inject
constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val clock: AppClock,
    private val ringtones: RingtoneRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmEditorUiState())
    val state: StateFlow<AlarmEditorUiState> = _state.asStateFlow()

    /** The title of the sound the draft points at, or null for the app default. */
    val soundTitle: StateFlow<String?> = titleOf { it.ringtoneUri }

    /** The title of the backup sound, or null for the system alarm sound. */
    val backupSoundTitle: StateFlow<String?> = titleOf { it.backupSoundUri }

    private var loaded = false

    /** Loads the alarm to edit; [alarmId] of [Alarm.NEW_ID] starts a blank draft. */
    fun load(alarmId: Long) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val existing = if (alarmId == Alarm.NEW_ID) null else repository.getAlarm(alarmId)
            val draft = existing?.let(AlarmDraft::fromAlarm) ?: AlarmDraft()
            _state.update {
                it.copy(
                    draft = draft,
                    original = draft,
                    loading = false,
                    nextRing = nextRingOf(draft),
                )
            }
        }
    }

    fun setTime(hour: Int, minute: Int) = edit { it.copy(hour = hour, minute = minute) }

    fun setName(name: String) = edit { it.copy(name = name) }

    fun setEmoji(emoji: String) = edit { it.copy(emoji = emoji) }

    fun toggleDay(day: DayOfWeek) = edit { draft ->
        val days = if (day in draft.days) draft.days - day else draft.days + day
        draft.copy(days = days)
    }

    fun setEveryDay() = edit { it.copy(days = DayOfWeek.entries.toSet()) }

    fun setOneShot() = edit { it.copy(days = emptySet()) }

    fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }

    fun setRingtone(uri: String?) = edit { it.copy(ringtoneUri = uri) }

    fun setVibrate(vibrate: Boolean) = edit { it.copy(vibrate = vibrate) }

    fun setVolume(ratio: Float) = edit {
        it.copy(volumeRatio = ratio.coerceIn(Alarm.VOLUME_MIN, Alarm.VOLUME_MAX))
    }

    fun setMemo(memo: String) = edit { it.copy(memo = memo) }

    fun setTtsEnabled(enabled: Boolean) = edit { it.copy(ttsEnabled = enabled) }

    fun setSnoozeEnabled(enabled: Boolean) = edit { it.copy(snoozeEnabled = enabled) }

    fun setSnoozeMinutes(minutes: Int) = edit {
        it.copy(snoozeMinutes = minutes.coerceIn(MIN_SNOOZE, MAX_SNOOZE))
    }

    fun setSnoozeLimit(limit: Int) = edit {
        it.copy(snoozeLimit = limit.coerceIn(0, MAX_SNOOZE_LIMIT))
    }

    fun setCrescendoEnabled(enabled: Boolean) = edit { it.copy(crescendoEnabled = enabled) }

    fun setCrescendoSeconds(seconds: Int) = edit {
        it.copy(crescendoSeconds = seconds.coerceIn(MIN_CRESCENDO, MAX_CRESCENDO))
    }

    fun setBackupSoundEnabled(enabled: Boolean) = edit { it.copy(backupSoundEnabled = enabled) }

    fun setBackupSound(uri: String?) = edit { it.copy(backupSoundUri = uri) }

    fun setTimeAnnouncementEnabled(enabled: Boolean) = edit {
        it.copy(timeAnnouncementEnabled = enabled)
    }

    fun setWakeUpCheckMinutes(minutes: Int) = edit {
        it.copy(wakeUpCheckMinutes = minutes.coerceIn(0, MAX_WAKE_UP_CHECK))
    }

    fun addMission(mission: Mission) = edit { draft ->
        if (!draft.canAddMission) draft else draft.copy(missions = draft.missions + mission)
    }

    fun removeMission(index: Int) = edit { draft ->
        if (index !in draft.missions.indices) draft
        else draft.copy(missions = draft.missions.filterIndexed { i, _ -> i != index })
    }

    fun replaceMission(index: Int, mission: Mission) = edit { draft ->
        if (index !in draft.missions.indices) draft
        else
            draft.copy(
                missions = draft.missions.toMutableList().also { it[index] = mission },
            )
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        val errors = current.draft.errors()
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true, errors = emptyList()) }
        viewModelScope.launch {
            val alarm = current.draft.toAlarm(clock.now())
            repository.upsertAlarm(alarm)
            val result =
                scheduler.rearm(
                    if (current.draft.isNew) RescheduleReason.ADD_ALARM
                    else RescheduleReason.MODIFY_ALARM,
                )
            _state.update {
                it.copy(
                    saving = false,
                    finished = EditorOutcome.Saved,
                    notArmed = result.next != null && !result.armed,
                )
            }
        }
    }

    /** The "could not arm" message has been shown; the host may now leave the editor. */
    fun notArmedShown() = _state.update { it.copy(notArmed = false) }

    fun delete() {
        val current = _state.value
        if (current.draft.isNew || current.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.deleteAlarm(current.draft.id)
            scheduler.rearm(RescheduleReason.DELETE_ALARM)
            _state.update { it.copy(saving = false, finished = EditorOutcome.Deleted) }
        }
    }

    private fun edit(transform: (AlarmDraft) -> AlarmDraft) {
        _state.update {
            val draft = transform(it.draft)
            it.copy(draft = draft, errors = emptyList(), nextRing = nextRingOf(draft))
        }
    }

    private fun nextRingOf(draft: AlarmDraft): Instant? =
        NextOccurrenceCalculator.nextOccurrence(
            draft.toAlarm(clock.now()).copy(isSkipped = false),
            clock.now(),
            clock.zone(),
        )

    private fun titleOf(uriOf: (AlarmDraft) -> String?): StateFlow<String?> =
        combine(_state.map { uriOf(it.draft) }, ringtones.observeAll()) { uri, rows ->
                rows.firstOrNull { it.uri == uri }?.title
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    companion object {
        const val MIN_SNOOZE = 1
        const val MAX_SNOOZE = 120
        const val MAX_SNOOZE_LIMIT = 9
        const val MIN_CRESCENDO = 1
        const val MAX_CRESCENDO = 600
        const val MAX_WAKE_UP_CHECK = 1440
    }
}
