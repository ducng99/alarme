package dev.tomng.alarme.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.tomng.alarme.core.designsystem.component.AlarmeScaffold
import dev.tomng.alarme.core.designsystem.component.PlainRow
import dev.tomng.alarme.core.designsystem.component.SegmentedGroup
import dev.tomng.alarme.core.designsystem.component.rememberListPadding
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.feature.settings.permissions.PermissionCenterState
import dev.tomng.alarme.feature.settings.permissions.PermissionItem
import dev.tomng.alarme.feature.settings.permissions.PermissionRow

/** Every grant Alarme can use, split into what alarms need and what extras need. */
@Composable
fun PermissionsScreen(
    permissions: PermissionCenterState,
    onOpenPermission: (PermissionItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (required, optional) = permissions.rows.partition { it.item.critical }
    AlarmeScaffold(
        title = stringResource(R.string.permissions_title),
        modifier = modifier,
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
        backTag = "permissions_done",
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("permissions_screen"),
            contentPadding =
                rememberListPadding(
                    innerPadding,
                    horizontal = Spacing.screen,
                    bottom = Spacing.xxl,
                ),
        ) {
            item(key = "required") {
                SectionHeader(R.string.permissions_required)
                PermissionGroup(required, onOpenPermission)
            }
            item(key = "optional") {
                SectionHeader(R.string.permissions_optional)
                PermissionGroup(optional, onOpenPermission)
            }
        }
    }
}

@Composable
private fun PermissionGroup(rows: List<PermissionRow>, onOpen: (PermissionItem) -> Unit) {
    SegmentedGroup {
        rows.forEach { permission ->
            row { shapes ->
                PlainRow(
                    shapes = shapes,
                    title = stringResource(permission.item.titleRes()),
                    supporting = stringResource(permission.item.bodyRes()),
                    leading = {
                        Icon(
                            imageVector = permission.item.icon(),
                            contentDescription = null,
                            tint =
                                if (permission.item.critical && !permission.granted)
                                    MaterialTheme.colorScheme.error
                                else LocalContentColor.current,
                        )
                    },
                    trailing = { GrantAction(permission, onOpen) },
                    modifier = Modifier.testTag("permission_${permission.item.name}"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GrantAction(permission: PermissionRow, onOpen: (PermissionItem) -> Unit) {
    AnimatedContent(
        targetState = permission.granted,
        transitionSpec = { (fadeIn() + scaleIn()) togetherWith fadeOut() },
        label = "grant",
    ) { granted ->
        if (granted) {
            val allowed = stringResource(R.string.permissions_allowed)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.semantics { contentDescription = allowed },
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            FilledTonalButton(
                onClick = { onOpen(permission.item) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.testTag("permission_allow_${permission.item.name}"),
            ) {
                Text(stringResource(R.string.permissions_allow))
            }
        }
    }
}
