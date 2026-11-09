package dev.tomng.alarme.core.alarmengine.scheduling

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the currently armed ring schedule id in **device-protected** storage so a pre-unlock
 * ring delivery (direct boot) can be validated against what was armed.
 */
interface ScheduleIdStore {
    fun save(id: String)

    fun load(): String?

    fun clear()
}

@Singleton
class DeviceProtectedScheduleIdStore
@Inject
constructor(
    @ApplicationContext context: Context,
) : ScheduleIdStore {

    private val prefs =
        context
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun save(id: String) {
        prefs.edit().putString(KEY_SCHEDULE_ID, id).apply()
    }

    override fun load(): String? = prefs.getString(KEY_SCHEDULE_ID, null)

    override fun clear() {
        prefs.edit().remove(KEY_SCHEDULE_ID).apply()
    }

    private companion object {
        const val FILE_NAME = "PrefScheduleId"
        const val KEY_SCHEDULE_ID = "SCHEDULE_ID"
    }
}
