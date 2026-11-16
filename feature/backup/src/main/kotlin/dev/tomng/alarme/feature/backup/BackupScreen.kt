package dev.tomng.alarme.feature.backup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.tomng.alarme.core.designsystem.component.AlarmeScaffold
import dev.tomng.alarme.core.designsystem.component.BannerTone
import dev.tomng.alarme.core.designsystem.component.InlineBanner
import dev.tomng.alarme.core.designsystem.component.rememberFeedback
import dev.tomng.alarme.core.designsystem.theme.Spacing

/**
 * Backup & restore. Both actions go through the system file picker (the caller owns the SAF
 * launchers), so this screen only renders state and reports outcomes. Restoring replaces every
 * alarm, so it asks first.
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
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    val feedback = rememberFeedback()

    LaunchedEffect(state) {
        when (state) {
            is BackupUiState.Exported,
            is BackupUiState.Imported -> feedback.confirm()
            is BackupUiState.Failed -> feedback.reject()
            else -> Unit
        }
    }

    if (confirmRestore) {
        RestoreDialog(
            onConfirm = {
                confirmRestore = false
                onImport()
            },
            onDismiss = { confirmRestore = false },
        )
    }

    AlarmeScaffold(
        title = stringResource(R.string.backup_title),
        subtitle = stringResource(R.string.backup_subtitle),
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
                    .padding(horizontal = Spacing.screen)
                    .testTag("backup_screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ActionCard(
                title = stringResource(R.string.backup_export),
                body = stringResource(R.string.backup_export_body),
                icon = Icons.Outlined.FileUpload,
                actionLabel = stringResource(R.string.backup_export_action),
                primary = true,
                enabled = !working,
                onClick = onExport,
                tag = "backup_export",
            )
            ActionCard(
                title = stringResource(R.string.backup_import),
                body = stringResource(R.string.backup_import_body),
                icon = Icons.Outlined.FileDownload,
                actionLabel = stringResource(R.string.backup_import_action),
                primary = false,
                enabled = !working,
                onClick = { confirmRestore = true },
                tag = "backup_import",
            )
            StatusArea(state)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.s),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.backup_import_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

/** Progress while working, then the outcome as a banner. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusArea(state: BackupUiState) {
    val enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
    val exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
    AnimatedContent(
        targetState = state,
        transitionSpec = { enter togetherWith exit },
        label = "backupStatus",
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) { shown ->
        when (shown) {
            BackupUiState.Working ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    modifier = Modifier.padding(Spacing.s),
                ) {
                    LoadingIndicator(modifier = Modifier.testTag("backup_progress"))
                    Text(
                        text = stringResource(R.string.backup_working),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            is BackupUiState.Exported ->
                InlineBanner(
                    title = stringResource(R.string.backup_exported_title),
                    body =
                        stringResource(
                            R.string.backup_exported,
                            shown.alarmCount,
                            shown.audioFileCount,
                        ),
                    icon = Icons.Outlined.CheckCircle,
                    tone = BannerTone.Success,
                    modifier = Modifier.testTag("backup_result"),
                )
            is BackupUiState.Imported ->
                InlineBanner(
                    title = stringResource(R.string.backup_imported_title),
                    body =
                        stringResource(
                            R.string.backup_imported,
                            shown.alarmCount,
                            shown.audioFileCount,
                        ),
                    icon = Icons.Outlined.CheckCircle,
                    tone = BannerTone.Success,
                    modifier = Modifier.testTag("backup_result"),
                )
            is BackupUiState.Failed ->
                InlineBanner(
                    title = stringResource(R.string.backup_failed_title),
                    body = stringResource(errorText(shown.error)),
                    icon = Icons.Outlined.ErrorOutline,
                    tone = BannerTone.Error,
                    modifier = Modifier.testTag("backup_error"),
                )
            BackupUiState.Idle -> Spacer(Modifier.fillMaxWidth())
        }
    }
}

/** One backup action: what it does, and a button that does it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionCard(
    title: String,
    body: String,
    icon: ImageVector,
    actionLabel: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ICON_SIZE),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val content: @Composable () -> Unit = {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(actionLabel)
            }
            if (primary) {
                Button(
                    onClick = onClick,
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().testTag(tag),
                ) {
                    content()
                }
            } else {
                FilledTonalButton(
                    onClick = onClick,
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().testTag(tag),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun RestoreDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Warning, contentDescription = null) },
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = { Text(stringResource(R.string.backup_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("backup_import_confirm")) {
                Text(stringResource(R.string.backup_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("backup_import_cancel")) {
                Text(stringResource(R.string.backup_confirm_cancel))
            }
        },
    )
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

private val ICON_SIZE = Spacing.xxl
