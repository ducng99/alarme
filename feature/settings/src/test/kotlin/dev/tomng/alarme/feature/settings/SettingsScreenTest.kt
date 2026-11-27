package dev.tomng.alarme.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.feature.settings.diagnostics.DiagnosticLabel
import dev.tomng.alarme.feature.settings.diagnostics.DiagnosticLine
import dev.tomng.alarme.feature.settings.optimize.OemGuide
import dev.tomng.alarme.feature.settings.permissions.PermissionCenterState
import dev.tomng.alarme.feature.settings.permissions.PermissionItem
import dev.tomng.alarme.feature.settings.permissions.PermissionRow
import dev.tomng.alarme.feature.settings.reliability.ReliabilityState
import dev.tomng.alarme.feature.settings.sound.SoundSettingsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenTest {

    @get:Rule val compose = createComposeRule()

    private val permissions =
        PermissionCenterState(
            rows =
                PermissionItem.entries.map {
                    PermissionRow(it, granted = it == PermissionItem.NOTIFICATIONS)
                },
            missingCount = PermissionItem.entries.size - 1,
            allGranted = false,
        )

    @Test
    fun `missing critical grants turn the status hero into a fix-it prompt`() {
        var fixed = false
        compose.setContent {
            SettingsScreen(
                permissions,
                ReliabilityState(),
                SettingsCallbacks(onFixPermissions = { fixed = true }),
            )
        }

        compose.onNodeWithText("Alarms can't ring reliably").assertIsDisplayed()
        compose.onNodeWithTag("settings_fix").performClick()

        assertThat(fixed).isTrue()
    }

    @Test
    fun `the alarm sound section toggles mute during mission`() {
        var toggled: Boolean? = null
        compose.setContent {
            SettingsScreen(
                permissions,
                ReliabilityState(),
                SettingsCallbacks(onToggleMuteDuringMission = { toggled = it }),
                sound = SoundSettingsState(muteDuringMission = true),
            )
        }

        compose
            .onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("toggle_mute_during_mission"))
        compose.onNodeWithText("Mute during mission").assertIsDisplayed()
        compose.onNodeWithTag("toggle_mute_during_mission").performClick()

        assertThat(toggled).isFalse()
    }

    @Test
    fun `granted required permissions hide the status banner`() {
        val requiredOnly =
            PermissionCenterState(
                rows = PermissionItem.entries.map { PermissionRow(it, granted = it.critical) },
                missingCount = PermissionItem.entries.count { !it.critical },
                allGranted = false,
            )
        compose.setContent { SettingsScreen(requiredOnly, ReliabilityState(), SettingsCallbacks()) }

        compose.onNodeWithTag("permissions_banner").assertDoesNotExist()
    }

    @Test
    fun `the permissions row opens the permissions screen`() {
        var opened = false
        compose.setContent {
            SettingsScreen(
                permissions,
                ReliabilityState(),
                SettingsCallbacks(onOpenPermissions = { opened = true }),
            )
        }

        compose.onNodeWithTag("open_permissions").performClick()

        assertThat(opened).isTrue()
    }

    @Test
    fun `permission rows offer allow only while not granted`() {
        var opened: PermissionItem? = null
        compose.setContent {
            PermissionsScreen(permissions, onOpenPermission = { opened = it }, onBack = {})
        }

        compose.onNodeWithTag("permission_NOTIFICATIONS").assertIsDisplayed()
        compose.onNodeWithTag("permission_allow_NOTIFICATIONS").assertDoesNotExist()
        compose.onNodeWithTag("permission_allow_EXACT_ALARM").performClick()

        assertThat(opened).isEqualTo(PermissionItem.EXACT_ALARM)
    }

    @Test
    fun `the power-off toggle reports its new value`() {
        var toggled: Boolean? = null
        compose.setContent {
            SettingsScreen(
                permissions,
                ReliabilityState(preventPowerOff = false),
                SettingsCallbacks(onTogglePreventPowerOff = { toggled = it }),
            )
        }

        compose
            .onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("toggle_prevent_power_off"))
        compose.onNodeWithTag("toggle_prevent_power_off").performClick()

        assertThat(toggled).isTrue()
    }

    @Test
    fun `an unarmed power-off toggle explains what is missing`() {
        compose.setContent {
            SettingsScreen(
                permissions,
                ReliabilityState(preventPowerOff = true, preventPowerOffArmed = false),
                SettingsCallbacks(),
            )
        }

        compose
            .onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("toggle_prevent_power_off"))
        compose
            .onNodeWithText("Needs accessibility and display-over-apps access")
            .assertIsDisplayed()
    }

    @Test
    fun `the block count is shown once something was blocked`() {
        compose.setContent {
            SettingsScreen(permissions, ReliabilityState(blockedCount = 3), SettingsCallbacks())
        }

        compose
            .onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("toggle_prevent_power_off"))
        compose.onNodeWithText("Blocked 3 times").assertIsDisplayed()
    }

    @Test
    fun `diagnostics and optimize links open their screens`() {
        var diagnostics = false
        var optimize = false
        compose.setContent {
            SettingsScreen(
                permissions,
                ReliabilityState(),
                SettingsCallbacks(
                    onOpenDiagnostics = { diagnostics = true },
                    onOpenOptimize = { optimize = true },
                ),
            )
        }

        compose.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("open_diagnostics"))
        compose.onNodeWithTag("open_diagnostics").performClick()
        compose.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("open_optimize"))
        compose.onNodeWithTag("open_optimize").performClick()

        assertThat(diagnostics).isTrue()
        assertThat(optimize).isTrue()
    }

    @Test
    fun `diagnostics lines show values and verdicts`() {
        compose.setContent {
            DiagnosticsScreen(
                lines =
                    listOf(
                        DiagnosticLine(DiagnosticLabel.BATTERY, "80%", healthy = true),
                        DiagnosticLine(DiagnosticLabel.STANDBY_BUCKET, "40", healthy = false),
                    ),
                onBack = {},
            )
        }

        compose.onNodeWithText("80%").assertIsDisplayed()
        compose.onNodeWithText("Check").assertIsDisplayed()
        compose.onNodeWithText("App standby bucket").assertIsDisplayed()
    }

    @Test
    fun `the optimize screen offers battery dnd and autostart`() {
        var battery = false
        var dnd = false
        var autostart = false
        compose.setContent {
            OptimizeScreen(
                guide = OemGuide.XIAOMI,
                batteryUnrestricted = true,
                dndGranted = false,
                onOpenBattery = { battery = true },
                onOpenDnd = { dnd = true },
                onOpenAutostart = { autostart = true },
                onBack = {},
            )
        }

        compose.onNodeWithTag("optimize_battery").performClick()
        compose.onNodeWithTag("optimize_dnd").performClick()
        compose.onNodeWithTag("optimize_autostart").performClick()

        assertThat(battery).isTrue()
        assertThat(dnd).isTrue()
        assertThat(autostart).isTrue()
        compose.onNodeWithText("Allowed").assertIsDisplayed()
        compose.onNodeWithText("Not allowed").assertIsDisplayed()
    }
}
