package dev.tomng.alarme.feature.settings.sound

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

data class SoundSettingsState(val muteDuringMission: Boolean = true)

@HiltViewModel
class SoundSettingsViewModel @Inject constructor(private val settings: SettingsRepository) :
    ViewModel() {

    val state: StateFlow<SoundSettingsState> =
        settings.settings
            .map { SoundSettingsState(muteDuringMission = it.muteDuringMission) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SoundSettingsState())

    fun setMuteDuringMission(enabled: Boolean) {
        viewModelScope.launch { settings.setMuteDuringMission(enabled) }
    }
}
