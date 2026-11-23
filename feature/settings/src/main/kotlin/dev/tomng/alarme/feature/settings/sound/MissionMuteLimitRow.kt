package dev.tomng.alarme.feature.settings.sound

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.data.settings.MISSION_MUTE_LIMIT_RANGE
import dev.tomng.alarme.feature.settings.R
import kotlin.math.roundToInt

/** The total mute-during-mission time per alarm, as a number of mission timeouts. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MissionMuteLimitRow(
    shapes: ListItemShapes,
    limit: Int,
    onLimitChange: (Int) -> Unit,
) {
    val range = MISSION_MUTE_LIMIT_RANGE
    // Dragging is local; the setting is written once when the thumb is released.
    var dragged by remember(limit) { mutableFloatStateOf(limit.toFloat()) }
    val shown = dragged.roundToInt()
    SegmentedListItem(
        shapes = shapes,
        leadingContent = { Icon(Icons.Outlined.Timer, contentDescription = null) },
        trailingContent = {
            Text(
                text = pluralStringResource(R.plurals.settings_mission_mute_limit, shown, shown),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 48.dp),
            )
        },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_mission_mute_limit_body))
                Slider(
                    value = dragged,
                    onValueChange = { dragged = it },
                    onValueChangeFinished = { onLimitChange(dragged.roundToInt()) },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = range.last - range.first - 1,
                    modifier = Modifier.fillMaxWidth().testTag("mission_mute_limit"),
                )
            }
        },
    ) {
        Text(stringResource(R.string.settings_mission_mute_limit))
    }
}
