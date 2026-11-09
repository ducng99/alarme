package dev.tomng.alarme.core.alarmengine.uninstall

import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceAdminControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val policy = context.getSystemService(DevicePolicyManager::class.java)
    private val controller = DeviceAdminController(context)

    @Test
    fun `an inactive admin reports inactive`() {
        assertThat(controller.isActive()).isFalse()
    }

    @Test
    fun `an active admin reports active`() {
        shadowOf(policy).setActiveAdmin(controller.component)

        assertThat(controller.isActive()).isTrue()
    }

    @Test
    fun `the request intent targets our admin component`() {
        val intent = controller.requestIntent()

        assertThat(intent.action).isEqualTo(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        assertThat(
                intent.getParcelableExtra<android.content.ComponentName>(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ),
            )
            .isEqualTo(controller.component)
    }

    @Test
    fun `disabling removes the admin`() {
        shadowOf(policy).setActiveAdmin(controller.component)

        controller.disable()

        assertThat(controller.isActive()).isFalse()
    }
}
