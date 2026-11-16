package dev.tomng.alarme.core.designsystem.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState

/**
 * The Material 3 Expressive segmented list: rows sit in their own tonal containers separated by a
 * small gap, with the outer corners of the group rounded more than the inner ones.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedGroup(modifier: Modifier = Modifier, content: SegmentedGroupScope.() -> Unit) {
    val scope = SegmentedGroupScope().apply(content)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        scope.rows.forEachIndexed { index, row ->
            row(ListItemDefaults.segmentedShapes(index = index, count = scope.rows.size))
        }
    }
}

/** A row that navigates somewhere, ending in a chevron or a value. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavRow(
    shapes: ListItemShapes,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = icon?.let { { Icon(it, contentDescription = null) } },
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        enabled = enabled,
        leadingContent = leading,
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        },
    ) {
        Text(title)
    }
}

/** A row whose whole surface toggles a switch. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SwitchRow(
    shapes: ListItemShapes,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = shapes,
        modifier =
            modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
        enabled = enabled,
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            AlarmeSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
    ) {
        Text(title)
    }
}

/** A row without its own action; [trailing] may hold buttons. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlainRow(
    shapes: ListItemShapes,
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    SegmentedListItem(
        shapes = shapes,
        modifier = modifier,
        leadingContent = leading,
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = trailing,
    ) {
        Text(title)
    }
}
