package dev.tomng.alarme.core.alarmengine.scheduling

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The snooze must survive reboot: the record is the boot restore source. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SnoozeStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = DeviceProtectedSnoozeStore(context)

    @Test
    fun `save then load round-trips the record`() {
        store.save(SnoozeRecord(alarmId = 7, finishAtMillis = 1_000L, count = 2))

        assertThat(store.load())
            .isEqualTo(SnoozeRecord(alarmId = 7, finishAtMillis = 1_000L, count = 2))
    }

    @Test
    fun `clear removes the record`() {
        store.save(SnoozeRecord(alarmId = 7, finishAtMillis = 1_000L, count = 2))
        store.clear()

        assertThat(store.load()).isNull()
    }

    @Test
    fun `absent record loads null`() {
        assertThat(store.load()).isNull()
    }
}
