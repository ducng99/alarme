package dev.tomng.alarme

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The grants the permission center offers are only grantable when the manifest asks for them: a
 * special access page does not even list an app that never declared the permission.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManifestPermissionsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun requested(): List<String> =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toList()

    @Test
    fun `the overlay grant the ring takeover needs is requested`() {
        assertThat(requested()).contains("android.permission.SYSTEM_ALERT_WINDOW")
    }

    @Test
    fun `the full screen intent grant is requested`() {
        assertThat(requested()).contains("android.permission.USE_FULL_SCREEN_INTENT")
    }
}
