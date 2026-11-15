package dev.tomng.alarme.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.GroupCard
import dev.tomng.alarme.core.designsystem.component.GroupDivider
import dev.tomng.alarme.core.designsystem.component.ScreenHeader
import dev.tomng.alarme.core.designsystem.component.SectionLabel
import dev.tomng.alarme.core.designsystem.component.SettingRow
import dev.tomng.alarme.feature.settings.optimize.OemGuide

@Composable
fun OptimizeScreen(
    guide: OemGuide,
    batteryUnrestricted: Boolean,
    dndGranted: Boolean,
    onOpenBattery: () -> Unit,
    onOpenDnd: () -> Unit,
    onOpenAutostart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .testTag("optimize_screen"),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.optimize_title),
                    navigation = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("optimize_done")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                            )
                        }
                    },
                )
            }
            item { SectionLabel(stringResource(R.string.optimize_section)) }
            item {
                GroupCard {
                    OptimizeRow(
                        title = stringResource(R.string.optimize_battery),
                        body = stringResource(R.string.optimize_battery_body),
                        granted = batteryUnrestricted,
                        tag = "optimize_battery",
                        onClick = onOpenBattery,
                    )
                    GroupDivider()
                    OptimizeRow(
                        title = stringResource(R.string.optimize_dnd),
                        body = stringResource(R.string.optimize_dnd_body),
                        granted = dndGranted,
                        tag = "optimize_dnd",
                        onClick = onOpenDnd,
                    )
                    GroupDivider()
                    OptimizeRow(
                        title = stringResource(R.string.optimize_autostart),
                        body =
                            if (guide == OemGuide.GENERIC) {
                                stringResource(R.string.optimize_generic_body)
                            } else {
                                stringResource(R.string.optimize_autostart_body)
                            },
                        granted = null,
                        tag = "optimize_autostart",
                        onClick = onOpenAutostart,
                    )
                }
            }
        }
    }
}

@Composable
private fun OptimizeRow(
    title: String,
    body: String,
    granted: Boolean?,
    tag: String,
    onClick: () -> Unit,
) {
    SettingRow(
        title = title,
        supporting = body,
        onClick = onClick,
        trailing = {
            if (granted != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color =
                        if (granted) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                    contentColor =
                        if (granted) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        text =
                            stringResource(
                                if (granted) R.string.optimize_granted
                                else R.string.optimize_missing,
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.optimize_open),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier.testTag(tag),
    )
}
