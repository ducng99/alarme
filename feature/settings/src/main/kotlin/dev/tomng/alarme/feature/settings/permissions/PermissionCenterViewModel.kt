package dev.tomng.alarme.feature.settings.permissions

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One capability and whether the user has granted it right now. */
data class PermissionRow(val item: PermissionItem, val granted: Boolean)

data class PermissionCenterState(
    val rows: List<PermissionRow> = emptyList(),
    val missingCount: Int = 0,
    val allGranted: Boolean = false,
) {
    val criticalMissing: List<PermissionItem>
        get() = rows.filter { it.item.critical && !it.granted }.map { it.item }

    fun isGranted(item: PermissionItem): Boolean = rows.any { it.item == item && it.granted }

    /** The setup steps still worth showing, in walk-through order. */
    fun onboardingSteps(): List<PermissionItem> =
        PermissionItem.onboarding.filter { item -> !isGranted(item) }
}

/**
 * The permission matrix. State is re-read on every [refresh] (the screen calls it on resume), so a
 * grant made in system settings shows up the moment the user comes back.
 */
@HiltViewModel
class PermissionCenterViewModel @Inject constructor(private val probe: PermissionProbe) :
    ViewModel() {

    private val mutableState = MutableStateFlow(PermissionCenterState())
    val state: StateFlow<PermissionCenterState> = mutableState.asStateFlow()

    fun refresh() {
        val rows = PermissionItem.entries.map { PermissionRow(it, probe.isGranted(it)) }
        val missing = rows.count { !it.granted }
        mutableState.value =
            PermissionCenterState(rows = rows, missingCount = missing, allGranted = missing == 0)
    }
}
