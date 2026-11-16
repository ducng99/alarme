package dev.tomng.alarme.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.tomng.alarme.core.designsystem.component.AlarmeScaffold
import dev.tomng.alarme.core.designsystem.component.BannerTone
import dev.tomng.alarme.core.designsystem.component.InlineBanner
import dev.tomng.alarme.core.designsystem.component.PlainRow
import dev.tomng.alarme.core.designsystem.component.SegmentedGroup
import dev.tomng.alarme.core.designsystem.component.StatusDot
import dev.tomng.alarme.core.designsystem.component.rememberListPadding
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.feature.settings.diagnostics.DiagnosticLabel
import dev.tomng.alarme.feature.settings.diagnostics.DiagnosticLine

@Composable
fun DiagnosticsScreen(
    lines: List<DiagnosticLine>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val issues = lines.count { !it.healthy }
    AlarmeScaffold(
        title = stringResource(R.string.diagnostics_title),
        modifier = modifier,
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
        backTag = "diagnostics_done",
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("diagnostics_screen"),
            contentPadding =
                rememberListPadding(
                    innerPadding,
                    horizontal = Spacing.screen,
                    bottom = Spacing.xxl,
                ),
        ) {
            item(key = "summary") {
                if (issues == 0) {
                    InlineBanner(
                        title = stringResource(R.string.diagnostics_all_healthy),
                        body = stringResource(R.string.diagnostics_all_healthy_body),
                        icon = Icons.Outlined.CheckCircle,
                        tone = BannerTone.Success,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                } else {
                    InlineBanner(
                        title = stringResource(R.string.diagnostics_issues, issues),
                        body = stringResource(R.string.diagnostics_issues_body),
                        icon = Icons.Outlined.ErrorOutline,
                        tone = BannerTone.Warning,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                }
            }
            item(key = "lines") {
                SectionHeader(R.string.diagnostics_section)
                SegmentedGroup {
                    lines
                        .sortedBy { it.healthy }
                        .forEach { line ->
                            row { shapes ->
                                PlainRow(
                                    shapes = shapes,
                                    title = stringResource(line.label.labelRes()),
                                    trailing = { DiagnosticValue(line) },
                                )
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticValue(line: DiagnosticLine) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            text = line.value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!line.healthy) {
            Text(
                text = stringResource(R.string.diagnostics_attention),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        StatusDot(ok = line.healthy)
    }
}

internal fun DiagnosticLabel.labelRes(): Int =
    when (this) {
        DiagnosticLabel.BATTERY -> R.string.diagnostics_label_battery
        DiagnosticLabel.CHARGING -> R.string.diagnostics_label_charging
        DiagnosticLabel.POWER_SAVE -> R.string.diagnostics_label_power_save
        DiagnosticLabel.DEVICE_IDLE -> R.string.diagnostics_label_device_idle
        DiagnosticLabel.STANDBY_BUCKET -> R.string.diagnostics_label_standby_bucket
        DiagnosticLabel.BACKGROUND_RESTRICTED -> R.string.diagnostics_label_background_restricted
        DiagnosticLabel.BATTERY_OPTIMIZATION -> R.string.diagnostics_label_battery_optimization
        DiagnosticLabel.EXACT_ALARM -> R.string.diagnostics_label_exact_alarm
        DiagnosticLabel.OVERLAY -> R.string.diagnostics_label_overlay
        DiagnosticLabel.MEMORY -> R.string.diagnostics_label_memory
        DiagnosticLabel.STORAGE -> R.string.diagnostics_label_storage
    }
