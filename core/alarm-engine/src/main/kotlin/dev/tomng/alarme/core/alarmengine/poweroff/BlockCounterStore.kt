package dev.tomng.alarme.core.alarmengine.poweroff

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How many times the blocker has pulled the user back from the power menu. */
interface BlockCounterStore {
    val count: Flow<Int>

    suspend fun increment()
}

/**
 * Device-protected preferences: the counter must survive a reboot-before-unlock, and it is written
 * from the accessibility service, which can run while the user is still on the lock screen.
 */
@Singleton
class PrefsBlockCounterStore
@Inject
constructor(
    @ApplicationContext context: Context,
) : BlockCounterStore {

    private val prefs =
        context
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(prefs.getInt(KEY_COUNT, 0))

    override val count: Flow<Int> = state.asStateFlow()

    override suspend fun increment() {
        val next = prefs.getInt(KEY_COUNT, 0) + 1
        prefs.edit { putInt(KEY_COUNT, next) }
        state.value = next
    }

    private companion object {
        const val FILE = "power_off_blocker"
        const val KEY_COUNT = "blocked_count"
    }
}
