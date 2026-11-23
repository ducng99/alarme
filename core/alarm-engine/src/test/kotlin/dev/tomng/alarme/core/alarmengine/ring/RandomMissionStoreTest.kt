package dev.tomng.alarme.core.alarmengine.ring

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pick must survive process death so a redelivered ring keeps the same missions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RandomMissionStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `save then load round-trips the pick across instances`() {
        val pick =
            RandomMissionPick(
                alarmId = 7L,
                chainSignature = 1_000,
                scheduleId = "s1",
                indices = listOf(3, 0, 2),
            )
        DeviceProtectedRandomMissionStore(context).save(pick)

        assertThat(DeviceProtectedRandomMissionStore(context).load()).isEqualTo(pick)
    }

    @Test
    fun `a cleared pick loads null`() {
        val store = DeviceProtectedRandomMissionStore(context)
        store.save(RandomMissionPick(7L, 1, "s1", listOf(0)))

        store.clear()

        assertThat(DeviceProtectedRandomMissionStore(context).load()).isNull()
    }

    @Test
    fun `absent pick loads null`() {
        assertThat(DeviceProtectedRandomMissionStore(context).load()).isNull()
    }
}
