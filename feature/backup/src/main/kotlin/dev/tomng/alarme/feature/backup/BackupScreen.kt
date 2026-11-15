package dev.tomng.alarme.feature.backup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.AlarmeScaffold
import dev.tomng.alarme.core.designsystem.component.GroupCard
import dev.tomng.alarme.core.designsystem.component.GroupDivider
import dev.tomng.alarme.core.designsystem.component.LeadingIcon
import dev.tomng.alarme.core.designsystem.component.SettingRow

/**
 * Backup & restore. Both actions go through the system file picker (the caller owns the SAF
 * launchers), so this screen only renders state and reports outcomes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupScreen(
    state: BackupUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val working = state == BackupUiState.Working
    AlarmeScaffold(
        title = stringResource(R.string.backup_title),
        modifier = modifier,
        onBack = onBack,
        backContentDescription = stringResource(R.string.backup_back),
        backTag = "backup_done",
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .testTag("backup_screen"),
        ) {
            Text(
                text = stringResource(R.string.backup_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 20.dp),
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                GroupCard {
                    SettingRow(
                        title = stringResource(R.string.backup_export),
                        supporting = stringResource(R.string.backup_export_body),
                        leading = { LeadingIcon(Icons.Outlined.FileUpload) },
                        enabled = !working,
                        onClick = onExport,
                        modifier = Modifier.testTag("backup_export"),
                    )
                    GroupDivider()
                    SettingRow(
                        title = stringResource(R.string.backup_import),
                        supporting = stringResource(R.string.backup_import_body),
                        leading = { LeadingIcon(Icons.Outlined.FileDownload) },
                        enabled = !working,
                        onClick = onImport,
                        modifier = Modifier.testTag("backup_import"),
                    )
                }
            }
            AnimatedVisibility(visible = working) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(40.dp).testTag("backup_progress"))
                    Text(
                        text = stringResource(R.string.backup_working),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (state) {
                is BackupUiState.Exported ->
                    ResultCard(
                        text =
                            stringResource(
                                R.string.backup_exported,
                                state.alarmCount,
                                state.audioFileCount,
                            ),
                        error = false,
                        tag = "backup_result",
                    )

                is BackupUiState.Imported ->
                    ResultCard(
                        text =
                            stringResource(
                                R.string.backup_imported,
                                state.alarmCount,
                                state.audioFileCount,
                            ),
                        error = false,
                        tag = "backup_result",
                    )

                is BackupUiState.Failed ->
                    ResultCard(
                        text = stringResource(errorText(state.error)),
                        error = true,
                        tag = "backup_error",
                    )

                BackupUiState.Idle,
                BackupUiState.Working -> Unit
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.backup_import_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(text: String, error: Boolean, tag: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color =
            if (error) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        contentColor =
            if (error) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Archive,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(tag),
            )
        }
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
