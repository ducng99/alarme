package dev.tomng.alarme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomng.alarme.core.data.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-shell state: whether first-run setup still has to be shown. */
@HiltViewModel
class AppViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {

    /** Null until the stored value has loaded, so the shell does not flash the wrong screen. */
    val onboardingDone: StateFlow<Boolean?> =
        settings.settings
            .map { it.onboardingDone }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun finishOnboarding() {
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }
}
