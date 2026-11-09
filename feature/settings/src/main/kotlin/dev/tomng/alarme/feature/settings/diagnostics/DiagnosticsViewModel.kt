package dev.tomng.alarme.feature.settings.diagnostics

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One diagnostic line: what was measured and whether it is a problem for alarm delivery. */
data class DiagnosticLine(val label: DiagnosticLabel, val value: String, val healthy: Boolean)

enum class DiagnosticLabel {
    BATTERY,
    CHARGING,
    POWER_SAVE,
    DEVICE_IDLE,
    STANDBY_BUCKET,
    BACKGROUND_RESTRICTED,
    BATTERY_OPTIMIZATION,
    EXACT_ALARM,
    OVERLAY,
    MEMORY,
    STORAGE,
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(private val reader: DiagnosticsReader) :
    ViewModel() {

    private val mutableState = MutableStateFlow<List<DiagnosticLine>>(emptyList())
    val lines: StateFlow<List<DiagnosticLine>> = mutableState.asStateFlow()

    fun refresh() {
        mutableState.value = reader.read().toLines()
    }
}

/**
 * Turns raw readings into lines with a verdict. The thresholds are deliberately generous: the point
 * is to flag the states that are known to delay alarms, not to grade the phone.
 */
fun DeviceDiagnostics.toLines(): List<DiagnosticLine> =
    listOf(
        DiagnosticLine(DiagnosticLabel.BATTERY, "$batteryPercent%", batteryPercent >= 15),
        DiagnosticLine(DiagnosticLabel.CHARGING, if (charging) "Yes" else "No", true),
        DiagnosticLine(
            DiagnosticLabel.POWER_SAVE,
            if (powerSaveMode) "On" else "Off",
            !powerSaveMode,
        ),
        DiagnosticLine(DiagnosticLabel.DEVICE_IDLE, if (deviceIdle) "Yes" else "No", !deviceIdle),
        DiagnosticLine(
            DiagnosticLabel.STANDBY_BUCKET,
            standbyBucket.toString(),
            standbyBucket in 0..ACTIVE_BUCKET_MAX,
        ),
        DiagnosticLine(
            DiagnosticLabel.BACKGROUND_RESTRICTED,
            if (backgroundRestricted) "Yes" else "No",
            !backgroundRestricted,
        ),
        DiagnosticLine(
            DiagnosticLabel.BATTERY_OPTIMIZATION,
            if (ignoringBatteryOptimizations) "Unrestricted" else "Optimized",
            ignoringBatteryOptimizations,
        ),
        DiagnosticLine(
            DiagnosticLabel.EXACT_ALARM,
            if (canScheduleExactAlarms) "Allowed" else "Not allowed",
            canScheduleExactAlarms,
        ),
        DiagnosticLine(
            DiagnosticLabel.OVERLAY,
            if (canDrawOverlays) "Allowed" else "Not allowed",
            canDrawOverlays,
        ),
        DiagnosticLine(DiagnosticLabel.MEMORY, "$memoryUsedPercent% used", memoryUsedPercent < 90),
        DiagnosticLine(
            DiagnosticLabel.STORAGE,
            "%.1f GB free".format(freeStorageGb),
            freeStorageGb > 0.5,
        ),
    )

/** Buckets 0..10 are active/working-set/frequent; 20+ is rare/restricted territory. */
private const val ACTIVE_BUCKET_MAX = 10
