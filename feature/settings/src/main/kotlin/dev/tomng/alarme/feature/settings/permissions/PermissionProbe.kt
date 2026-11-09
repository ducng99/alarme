package dev.tomng.alarme.feature.settings.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the live grant state of every [PermissionItem]; never cached, the user can revoke anytime.
 */
interface PermissionProbe {
    fun isGranted(item: PermissionItem): Boolean
}

@Singleton
class AndroidPermissionProbe
@Inject
constructor(
    @ApplicationContext private val context: Context,
) : PermissionProbe {

    override fun isGranted(item: PermissionItem): Boolean =
        when (item) {
            PermissionItem.NOTIFICATIONS ->
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            PermissionItem.EXACT_ALARM ->
                context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
            PermissionItem.FULL_SCREEN_INTENT ->
                context
                    .getSystemService(NotificationManager::class.java)
                    ?.canUseFullScreenIntent() == true
            PermissionItem.OVERLAY -> Settings.canDrawOverlays(context)
            PermissionItem.DND ->
                context
                    .getSystemService(NotificationManager::class.java)
                    ?.isNotificationPolicyAccessGranted == true
            PermissionItem.BATTERY ->
                context
                    .getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true
            PermissionItem.CAMERA -> granted(Manifest.permission.CAMERA)
            PermissionItem.MICROPHONE -> granted(Manifest.permission.RECORD_AUDIO)
        }

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
