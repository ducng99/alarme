package dev.tomng.alarme.feature.settings.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The capabilities an alarm app needs to be reliable, in the order the user should grant them. */
enum class PermissionItem {
    NOTIFICATIONS,
    EXACT_ALARM,
    FULL_SCREEN_INTENT,
    OVERLAY,
    DND,
    BATTERY,
    CAMERA,
    MICROPHONE;

    /** Without these an alarm can ring late, silently, or not at all. */
    val critical: Boolean
        get() = this == NOTIFICATIONS || this == EXACT_ALARM || this == FULL_SCREEN_INTENT

    companion object {
        /** What first-run setup walks through, in order. */
        val onboarding = listOf(NOTIFICATIONS, EXACT_ALARM, FULL_SCREEN_INTENT, BATTERY)
    }
}

/** Where the user goes to grant one capability. */
interface PermissionIntents {
    fun forItem(item: PermissionItem): Intent
}

/**
 * Per-item deep links. Each one lands on the exact system screen for that grant — a permission
 * center that dumps the user on the app-details page is a permission center nobody finishes.
 */
@Singleton
class AndroidPermissionIntents
@Inject
constructor(
    @ApplicationContext private val context: Context,
) : PermissionIntents {

    override fun forItem(item: PermissionItem): Intent =
        when (item) {
            PermissionItem.NOTIFICATIONS ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

            PermissionItem.EXACT_ALARM ->
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${context.packageName}"))

            PermissionItem.FULL_SCREEN_INTENT ->
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:${context.packageName}"))

            PermissionItem.OVERLAY ->
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    .setData(Uri.parse("package:${context.packageName}"))

            PermissionItem.DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

            PermissionItem.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

            PermissionItem.CAMERA,
            PermissionItem.MICROPHONE ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
        }
}
