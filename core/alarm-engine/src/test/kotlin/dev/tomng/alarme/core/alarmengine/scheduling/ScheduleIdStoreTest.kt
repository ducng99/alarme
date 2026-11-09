package dev.tomng.alarme.core.alarmengine.scheduling

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The schedule pointer survives in device-protected storage (readable before first unlock). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduleIdStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `save load clear`() {
        val store = DeviceProtectedScheduleIdStore(context)

        assertThat(store.load()).isNull()

        store.save("dev1_2_0600_2310")
        assertThat(store.load()).isEqualTo("dev1_2_0600_2310")

        // a second instance over the same protected storage sees the same pointer
        val second = DeviceProtectedScheduleIdStore(context)
        assertThat(second.load()).isEqualTo("dev1_2_0600_2310")

        store.clear()
        assertThat(store.load()).isNull()
    }
}
