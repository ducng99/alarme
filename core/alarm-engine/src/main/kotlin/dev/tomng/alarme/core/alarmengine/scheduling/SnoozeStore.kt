package dev.tomng.alarme.core.alarmengine.scheduling

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * The live snooze, persisted in device-protected storage so a reboot mid-snooze can re-arm it:
 * which alarm, when the snooze finishes, and the snooze count so far.
 */
interface SnoozeStore {
    fun save(record: SnoozeRecord)

    fun load(): SnoozeRecord?

    fun clear()
}

data class SnoozeRecord(
    val alarmId: Long,
    val finishAtMillis: Long,
    val count: Int,
)

@Singleton
class DeviceProtectedSnoozeStore
@Inject
constructor(
    @ApplicationContext context: Context,
) : SnoozeStore {

    private val prefs =
        context
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun save(record: SnoozeRecord) {
        prefs
            .edit()
            .putLong(KEY_ALARM_ID, record.alarmId)
            .putLong(KEY_FINISH_AT, record.finishAtMillis)
            .putInt(KEY_COUNT, record.count)
            .apply()
    }

    override fun load(): SnoozeRecord? {
        if (!prefs.contains(KEY_ALARM_ID)) return null
        return SnoozeRecord(
            alarmId = prefs.getLong(KEY_ALARM_ID, -1L),
            finishAtMillis = prefs.getLong(KEY_FINISH_AT, 0L),
            count = prefs.getInt(KEY_COUNT, 0),
        )
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "PrefSnooze"
        const val KEY_ALARM_ID = "SNOOZE_ALARM_ID"
        const val KEY_FINISH_AT = "SNOOZE_FINISH_AT"
        const val KEY_COUNT = "SNOOZE_COUNT"
    }
}

/** The stored finish time as an instant, for the boot restore. */
fun SnoozeRecord.finishAt(): Instant = Instant.fromEpochMilliseconds(finishAtMillis)
