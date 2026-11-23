package dev.tomng.alarme.feature.settings.sound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomng.alarme.core.data.repository.SettingsRepository
import dev.tomng.alarme.core.data.settings.DEFAULT_MISSION_MUTE_LIMIT
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SoundSettingsState(
    val muteDuringMission: Boolean = true,
    val missionMuteLimit: Int = DEFAULT_MISSION_MUTE_LIMIT,
)

@HiltViewModel
class SoundSettingsViewModel @Inject constructor(private val settings: SettingsRepository) :
    ViewModel() {

    val state: StateFlow<SoundSettingsState> =
        settings.settings
            .map {
                SoundSettingsState(
                    muteDuringMission = it.muteDuringMission,
                    missionMuteLimit = it.missionMuteLimit,
                )
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SoundSettingsState())

    fun setMuteDuringMission(enabled: Boolean) {
        viewModelScope.launch { settings.setMuteDuringMission(enabled) }
    }

    fun setMissionMuteLimit(minutes: Int) {
        viewModelScope.launch { settings.setMissionMuteLimit(minutes) }
    }
}
