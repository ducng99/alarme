package dev.tomng.alarme.feature.settings.diagnostics

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The handful of device facts that actually decide whether an alarm rings on time. Shown to the
 * user as a self-diagnostic ("your alarm may be late because the app is in standby bucket 40") and
 * useful when someone reports "it didn't ring".
 */
data class DeviceDiagnostics(
    val batteryPercent: Int,
    val charging: Boolean,
    val powerSaveMode: Boolean,
    val deviceIdle: Boolean,
    val standbyBucket: Int,
    val backgroundRestricted: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val canScheduleExactAlarms: Boolean,
    val canDrawOverlays: Boolean,
    val memoryUsedPercent: Int,
    val freeStorageGb: Double,
)

interface DiagnosticsReader {
    fun read(): DeviceDiagnostics
}

@Singleton
class AndroidDiagnosticsReader
@Inject
constructor(
    @ApplicationContext private val context: Context,
) : DiagnosticsReader {

    override fun read(): DeviceDiagnostics {
        val battery = context.getSystemService(BatteryManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val activity = context.getSystemService(ActivityManager::class.java)
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { activity?.getMemoryInfo(it) }
        val storage = StatFs(context.filesDir.absolutePath)

        return DeviceDiagnostics(
            batteryPercent =
                battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1,
            charging = battery?.isCharging == true,
            powerSaveMode = power?.isPowerSaveMode == true,
            deviceIdle = power?.isDeviceIdleMode == true,
            standbyBucket = usage?.appStandbyBucket ?: -1,
            backgroundRestricted = activity?.isBackgroundRestricted == true,
            ignoringBatteryOptimizations =
                power?.isIgnoringBatteryOptimizations(context.packageName) == true,
            canScheduleExactAlarms = alarm?.canScheduleExactAlarms() == true,
            canDrawOverlays = Settings.canDrawOverlays(context),
            memoryUsedPercent =
                if (memory.totalMem > 0) {
                    ((memory.totalMem - memory.availMem) * 100 / memory.totalMem).toInt()
                } else {
                    -1
                },
            freeStorageGb = storage.availableBytes / GB,
        )
    }

    private companion object {
        const val GB = 1024.0 * 1024.0 * 1024.0
    }
}
