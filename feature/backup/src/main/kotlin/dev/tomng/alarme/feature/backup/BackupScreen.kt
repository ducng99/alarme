package dev.tomng.alarme.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Backup & restore. Both actions go through the system file picker (the caller owns the SAF
 * launchers), so this screen only renders state and reports outcomes.
 */
@Composable
fun BackupScreen(
    state: BackupUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val working = state == BackupUiState.Working
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("backup_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.backup_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack, modifier = Modifier.testTag("backup_done")) {
                Text(stringResource(R.string.backup_back))
            }
        }

        Text(
            text = stringResource(R.string.backup_explainer),
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = onExport,
            enabled = !working,
            modifier = Modifier.fillMaxWidth().testTag("backup_export"),
        ) {
            Text(stringResource(R.string.backup_export))
        }
        OutlinedButton(
            onClick = onImport,
            enabled = !working,
            modifier = Modifier.fillMaxWidth().testTag("backup_import"),
        ) {
            Text(stringResource(R.string.backup_import))
        }

        if (working) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("backup_progress"))
                Text(stringResource(R.string.backup_working))
            }
        }

        when (state) {
            is BackupUiState.Exported ->
                Text(
                    text =
                        stringResource(
                            R.string.backup_exported,
                            state.alarmCount,
                            state.audioFileCount,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("backup_result"),
                )

            is BackupUiState.Imported ->
                Text(
                    text =
                        stringResource(
                            R.string.backup_imported,
                            state.alarmCount,
                            state.audioFileCount,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("backup_result"),
                )

            is BackupUiState.Failed ->
                Text(
                    text = stringResource(errorText(state.error)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("backup_error"),
                )

            BackupUiState.Idle,
            BackupUiState.Working -> Unit
        }

        Text(
            text = stringResource(R.string.backup_import_warning),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun errorText(error: BackupError): Int =
    when (error) {
        BackupError.NOT_AN_ARCHIVE -> R.string.backup_error_not_archive
        BackupError.MISSING_DATA -> R.string.backup_error_missing_data
        BackupError.INVALID_DATA -> R.string.backup_error_invalid
        BackupError.NEWER_VERSION -> R.string.backup_error_newer
        BackupError.TOO_MANY_ALARMS -> R.string.backup_error_too_many
        BackupError.TOO_LARGE -> R.string.backup_error_too_large
        BackupError.MISSING_AUDIO -> R.string.backup_error_missing_audio
        BackupError.CANNOT_OPEN -> R.string.backup_error_cannot_open
        BackupError.CANNOT_WRITE -> R.string.backup_error_cannot_write
    }
