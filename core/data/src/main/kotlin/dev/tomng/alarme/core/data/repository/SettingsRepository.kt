package dev.tomng.alarme.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.tomng.alarme.core.data.settings.AppSettings
import dev.tomng.alarme.core.data.settings.AudioOutputPreference
import dev.tomng.alarme.core.model.alarm.AutoDismissOption
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reads and writes [AppSettings] (DataStore-backed) and owns the stable installation id. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setMute(enabled: Boolean, limit: Int)

    suspend fun setMuteDuringMission(enabled: Boolean)

    suspend fun setAutoDismiss(option: AutoDismissOption)

    suspend fun setAudioOutput(output: AudioOutputPreference)

    suspend fun setPreventPowerOff(enabled: Boolean)

    suspend fun setPreventUninstall(enabled: Boolean)

    suspend fun setDefaultRingtoneId(id: Long?)

    suspend fun setOnboardingDone(done: Boolean)

    suspend fun deviceId(): String
}

class SettingsRepositoryImpl
@javax.inject.Inject
constructor(
    @dev.tomng.alarme.core.common.di.ApplicationScope private val scope: CoroutineScope,
    file: java.io.File,
) : SettingsRepository {

    private val dataStore: DataStore<Preferences> = dataStoreFor(scope, file)

    private val deviceMutex = Mutex()

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs -> prefs.toSettings() }

    override suspend fun setMute(enabled: Boolean, limit: Int) {
        require(limit in 0..9) { "mute limit must be in 0..9" }
        dataStore.edit { prefs ->
            prefs[MUTE_ENABLED] = enabled
            prefs[MUTE_LIMIT] = limit
        }
    }

    override suspend fun setMuteDuringMission(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[MUTE_DURING_MISSION] = enabled }
    }

    override suspend fun setAutoDismiss(option: AutoDismissOption) {
        dataStore.edit { prefs ->
            when (option) {
                AutoDismissOption.Off -> {
                    prefs[AUTO_DISMISS_MINUTES] = OFF
                    prefs.remove(AUTO_DISMISS_MINUTES)
                }
                is AutoDismissOption.After -> prefs[AUTO_DISMISS_MINUTES] = option.minutes
            }
        }
    }

    override suspend fun setAudioOutput(output: AudioOutputPreference) {
        dataStore.edit { prefs -> prefs[AUDIO_OUTPUT] = output.name }
    }

    override suspend fun setPreventPowerOff(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PREVENT_POWER_OFF] = enabled }
    }

    override suspend fun setPreventUninstall(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PREVENT_UNINSTALL] = enabled }
    }

    override suspend fun setDefaultRingtoneId(id: Long?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(DEFAULT_RINGTONE_ID) else prefs[DEFAULT_RINGTONE_ID] = id
        }
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { prefs -> prefs[ONBOARDING_DONE] = done }
    }

    override suspend fun deviceId(): String = deviceMutex.withLock {
        val existing = dataStore.data.first()[DEVICE_ID]
        if (existing != null) return@withLock existing
        val fresh = UUID.randomUUID().toString()
        dataStore.edit { prefs -> prefs[DEVICE_ID] = fresh }
        fresh
    }

    private fun Preferences.toSettings() =
        AppSettings(
            muteEnabled = this[MUTE_ENABLED] ?: true,
            muteLimit = this[MUTE_LIMIT] ?: 3,
            muteDuringMission = this[MUTE_DURING_MISSION] ?: true,
            autoDismiss =
                when (val minutes = this[AUTO_DISMISS_MINUTES]) {
                    null,
                    OFF -> AutoDismissOption.Off
                    else ->
                        runCatching { AutoDismissOption.After(minutes) }
                            .getOrDefault(AutoDismissOption.Off)
                },
            audioOutput =
                this[AUDIO_OUTPUT]?.let {
                    runCatching { AudioOutputPreference.valueOf(it) }.getOrNull()
                } ?: AudioOutputPreference.ALARM_STREAM,
            preventPowerOff = this[PREVENT_POWER_OFF] ?: false,
            preventUninstall = this[PREVENT_UNINSTALL] ?: false,
            defaultRingtoneId = this[DEFAULT_RINGTONE_ID],
            onboardingDone = this[ONBOARDING_DONE] ?: false,
        )

    internal companion object {
        private val stores =
            java.util.concurrent.ConcurrentHashMap<String, DataStore<Preferences>>()

        /** One DataStore per file across the process (DataStore demands single ownership). */
        fun dataStoreFor(
            scope: CoroutineScope,
            file: java.io.File,
        ): DataStore<Preferences> =
            stores.computeIfAbsent(file.absolutePath) {
                androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                    scope = scope,
                ) {
                    file
                }
            }

        val MUTE_ENABLED = booleanPreferencesKey("mute_enabled")
        val MUTE_LIMIT = intPreferencesKey("mute_limit")
        val MUTE_DURING_MISSION = booleanPreferencesKey("mute_during_mission")
        val AUTO_DISMISS_MINUTES = intPreferencesKey("auto_dismiss_minutes")
        val AUDIO_OUTPUT = stringPreferencesKey("audio_output")
        val PREVENT_POWER_OFF = booleanPreferencesKey("prevent_power_off")
        val PREVENT_UNINSTALL = booleanPreferencesKey("prevent_uninstall")
        val DEFAULT_RINGTONE_ID = longPreferencesKey("default_ringtone_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        const val OFF = -1
    }
}
