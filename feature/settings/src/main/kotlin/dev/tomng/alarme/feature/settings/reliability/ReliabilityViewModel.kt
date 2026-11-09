package dev.tomng.alarme.feature.settings.reliability

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomng.alarme.core.alarmengine.poweroff.BlockCounterStore
import dev.tomng.alarme.core.alarmengine.poweroff.BlockerChannel
import dev.tomng.alarme.core.alarmengine.uninstall.UninstallGuard
import dev.tomng.alarme.core.data.repository.SettingsRepository
import dev.tomng.alarme.feature.settings.permissions.PermissionItem
import dev.tomng.alarme.feature.settings.permissions.PermissionProbe
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReliabilityState(
    val preventPowerOff: Boolean = false,
    val preventPowerOffArmed: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val preventUninstall: Boolean = false,
    val blockedCount: Int = 0,
)

/**
 * The two "cheat prevention" switches. Both are optional and both degrade honestly: the power-off
 * blocker needs the accessibility grant *and* the overlay grant, and the uninstall guard is only
 * ever reported as on when the system says the admin is actually active.
 */
@HiltViewModel
class ReliabilityViewModel
@Inject
constructor(
    private val settings: SettingsRepository,
    private val channel: BlockerChannel,
    private val counter: BlockCounterStore,
    private val guard: UninstallGuard,
    private val probe: PermissionProbe,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ReliabilityState())
    val state: StateFlow<ReliabilityState> = mutableState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val current = settings.settings.first()
            val accessibility = probe.isGranted(PermissionItem.OVERLAY) && channel.isConnected()
            mutableState.value =
                ReliabilityState(
                    preventPowerOff = current.preventPowerOff,
                    preventPowerOffArmed = current.preventPowerOff && accessibility,
                    accessibilityGranted = channel.isConnected(),
                    overlayGranted = probe.isGranted(PermissionItem.OVERLAY),
                    preventUninstall = guard.isActive(),
                    blockedCount = counter.count.first(),
                )
        }
    }

    fun setPreventPowerOff(enabled: Boolean) {
        viewModelScope.launch {
            settings.setPreventPowerOff(enabled)
            refresh()
        }
    }

    /** Enabling needs the system dialog; the screen launches [adminRequestIntent] and re-reads. */
    fun setPreventUninstall(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                settings.setPreventUninstall(true)
            } else {
                guard.disable()
                settings.setPreventUninstall(false)
            }
            refresh()
        }
    }

    fun adminRequestIntent(): Intent = guard.requestIntent()
}
