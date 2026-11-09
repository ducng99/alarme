package dev.tomng.alarme.feature.settings.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiagnosticsLinesTest {

    private val healthy =
        DeviceDiagnostics(
            batteryPercent = 80,
            charging = false,
            powerSaveMode = false,
            deviceIdle = false,
            standbyBucket = 5,
            backgroundRestricted = false,
            ignoringBatteryOptimizations = true,
            canScheduleExactAlarms = true,
            canDrawOverlays = true,
            memoryUsedPercent = 40,
            freeStorageGb = 12.0,
        )

    @Test
    fun `a healthy device has no unhealthy lines`() {
        assertThat(healthy.toLines().filterNot { it.healthy }).isEmpty()
    }

    @Test
    fun `every diagnostic is reported`() {
        assertThat(healthy.toLines().map { it.label })
            .containsExactlyElementsIn(DiagnosticLabel.entries)
    }

    @Test
    fun `a restricted standby bucket is flagged`() {
        val line =
            healthy.copy(standbyBucket = 40).toLines().single {
                it.label == DiagnosticLabel.STANDBY_BUCKET
            }

        assertThat(line.healthy).isFalse()
        assertThat(line.value).isEqualTo("40")
    }

    @Test
    fun `a missing exact-alarm grant is flagged`() {
        val line =
            healthy.copy(canScheduleExactAlarms = false).toLines().single {
                it.label == DiagnosticLabel.EXACT_ALARM
            }

        assertThat(line.healthy).isFalse()
        assertThat(line.value).isEqualTo("Not allowed")
    }

    @Test
    fun `battery optimization is flagged when the app is optimized`() {
        val line =
            healthy.copy(ignoringBatteryOptimizations = false).toLines().single {
                it.label == DiagnosticLabel.BATTERY_OPTIMIZATION
            }

        assertThat(line.healthy).isFalse()
        assertThat(line.value).isEqualTo("Optimized")
    }

    @Test
    fun `a nearly full disk is flagged`() {
        val line =
            healthy.copy(freeStorageGb = 0.2).toLines().single {
                it.label == DiagnosticLabel.STORAGE
            }

        assertThat(line.healthy).isFalse()
    }

    @Test
    fun `a low battery is flagged`() {
        val line =
            healthy.copy(batteryPercent = 5).toLines().single {
                it.label == DiagnosticLabel.BATTERY
            }

        assertThat(line.healthy).isFalse()
    }
}
