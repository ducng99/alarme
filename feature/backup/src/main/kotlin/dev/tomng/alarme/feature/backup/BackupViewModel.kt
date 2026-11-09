package dev.tomng.alarme.feature.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the backup screen shows. */
sealed interface BackupUiState {
    data object Idle : BackupUiState

    data object Working : BackupUiState

    data class Exported(val alarmCount: Int, val audioFileCount: Int) : BackupUiState

    data class Imported(val alarmCount: Int, val audioFileCount: Int) : BackupUiState

    data class Failed(val error: BackupError) : BackupUiState
}

@HiltViewModel
class BackupViewModel @Inject constructor(private val manager: BackupOperations) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun export(uri: Uri) {
        launchOperation {
            val summary = manager.export(uri)
            BackupUiState.Exported(summary.alarmCount, summary.audioFileCount)
        }
    }

    fun import(uri: Uri) {
        launchOperation {
            val summary = manager.import(uri)
            BackupUiState.Imported(summary.alarmCount, summary.audioFileCount)
        }
    }

    fun clear() {
        if (_state.value != BackupUiState.Working) _state.value = BackupUiState.Idle
    }

    private fun launchOperation(block: suspend () -> BackupUiState) {
        if (_state.value == BackupUiState.Working) return
        _state.value = BackupUiState.Working
        viewModelScope.launch {
            _state.value =
                try {
                    block()
                } catch (e: BackupFormatException) {
                    BackupUiState.Failed(e.error)
                } catch (e: Exception) {
                    BackupUiState.Failed(BackupError.CANNOT_OPEN)
                }
        }
    }
}
