package dev.tomng.alarme.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.tomng.alarme.core.designsystem.component.AlarmeScaffold
import dev.tomng.alarme.core.designsystem.component.SegmentedGroup
import dev.tomng.alarme.core.designsystem.component.StatusDot
import dev.tomng.alarme.core.designsystem.component.rememberListPadding
import dev.tomng.alarme.core.designsystem.theme.Spacing
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
    AlarmeScaffold(
        title = stringResource(R.string.optimize_title),
        modifier = modifier,
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
        backTag = "optimize_done",
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("optimize_screen"),
            contentPadding =
                rememberListPadding(
                    innerPadding,
                    horizontal = Spacing.screen,
                    bottom = Spacing.xxl,
                ),
        ) {
            item(key = "rows") {
                SectionHeader(R.string.optimize_section)
                SegmentedGroup {
                    row { shapes ->
                        OptimizeRow(
                            shapes = shapes,
                            title = stringResource(R.string.optimize_battery),
                            body = stringResource(R.string.optimize_battery_body),
                            icon = Icons.Outlined.BatterySaver,
                            granted = batteryUnrestricted,
                            onClick = onOpenBattery,
                            tag = "optimize_battery",
                        )
                    }
                    row { shapes ->
                        OptimizeRow(
                            shapes = shapes,
                            title = stringResource(R.string.optimize_dnd),
                            body = stringResource(R.string.optimize_dnd_body),
                            icon = Icons.Outlined.DoNotDisturbOn,
                            granted = dndGranted,
                            onClick = onOpenDnd,
                            tag = "optimize_dnd",
                        )
                    }
                    row { shapes ->
                        OptimizeRow(
                            shapes = shapes,
                            title = stringResource(R.string.optimize_autostart),
                            body =
                                stringResource(
                                    if (guide == OemGuide.GENERIC) R.string.optimize_generic_body
                                    else R.string.optimize_autostart_body,
                                ),
                            icon = Icons.Outlined.RestartAlt,
                            granted = null,
                            onClick = onOpenAutostart,
                            tag = "optimize_autostart",
                        )
                    }
                }
            }
        }
    }
}

/** A row that opens a system screen; [granted] null means the state cannot be read. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptimizeRow(
    shapes: ListItemShapes,
    title: String,
    body: String,
    icon: ImageVector,
    granted: Boolean?,
    onClick: () -> Unit,
    tag: String,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = Modifier.testTag(tag),
        leadingContent = { Icon(icon, contentDescription = null) },
        overlineContent =
            granted?.let { ok ->
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        StatusDot(ok = ok)
                        Text(
                            text =
                                stringResource(
                                    if (ok) R.string.optimize_granted
                                    else R.string.optimize_missing,
                                ),
                            color =
                                if (ok) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
        supportingContent = { Text(body) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = stringResource(R.string.optimize_open),
            )
        },
    ) {
        Text(title)
    }
}
