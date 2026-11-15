package dev.tomng.alarme.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.GroupCard
import dev.tomng.alarme.core.designsystem.component.GroupDivider
import dev.tomng.alarme.core.designsystem.component.ScreenHeader
import dev.tomng.alarme.core.designsystem.component.SectionLabel
import dev.tomng.alarme.feature.settings.diagnostics.DiagnosticLabel
import dev.tomng.alarme.feature.settings.diagnostics.DiagnosticLine

@Composable
fun DiagnosticsScreen(
    lines: List<DiagnosticLine>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .testTag("diagnostics_screen"),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.diagnostics_title),
                    navigation = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("diagnostics_done"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                            )
                        }
                    },
                )
            }
            item { SectionLabel(stringResource(R.string.diagnostics_section)) }
            item {
                GroupCard {
                    lines.forEachIndexed { index, line ->
                        if (index > 0) GroupDivider()
                        DiagnosticRow(line)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(line: DiagnosticLine) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(line.label.labelRes()),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = line.value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color =
                if (line.healthy) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            contentColor =
                if (line.healthy) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Text(
                text =
                    stringResource(
                        if (line.healthy) R.string.diagnostics_ok
                        else R.string.diagnostics_attention,
                    ),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
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
