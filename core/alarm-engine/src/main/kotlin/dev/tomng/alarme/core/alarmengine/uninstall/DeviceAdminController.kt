package dev.tomng.alarme.core.alarmengine.uninstall

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The "prevent uninstall" guard as the settings screen sees it. */
interface UninstallGuard {
    fun isActive(): Boolean

    fun requestIntent(): Intent

    fun disable()
}

/**
 * Owns the "prevent uninstall" device admin: whether it is active, the system intent that asks the
 * user to activate it, and the in-app way to give it back. The state is always read from the system
 * — never cached — because the user can deactivate the admin from Settings at any time.
 */
@Singleton
class DeviceAdminController
@Inject
constructor(
    @ApplicationContext private val context: Context,
) : UninstallGuard {
    private val policy = context.getSystemService(DevicePolicyManager::class.java)

    val component: ComponentName = ComponentName(context, AdminReceiver::class.java)

    override fun isActive(): Boolean = policy?.isAdminActive(component) == true

    override fun requestIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(
                    dev.tomng.alarme.core.alarmengine.R.string.alarme_admin_explanation,
                ),
            )

    override fun disable() {
        runCatching { policy?.removeActiveAdmin(component) }
    }
}
