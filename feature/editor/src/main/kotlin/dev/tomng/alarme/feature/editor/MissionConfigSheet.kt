package dev.tomng.alarme.feature.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.IconBadge
import dev.tomng.alarme.core.designsystem.component.rememberFeedback
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.mission.Mission

/**
 * Per-type mission configuration in a full-height sheet. The sheet edits a copy and hands back the
 * finished mission on Done; dismissing it any other way discards the edits.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MissionConfigSheet(
    mission: Mission,
    onDismiss: () -> Unit,
    onConfirm: (Mission) -> Unit,
) {
    var current by remember(mission) { mutableStateOf(mission) }
    val feedback = rememberFeedback()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("mission_config"),
    ) {
        Column(
            modifier =
                Modifier.weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.l)
                    .testTag("mission_config_scroll"),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            MissionHeader(current)
            MissionFields(mission = current, onChange = { current = it })
            TimeLimitSection(
                seconds = current.timeLimitSeconds,
                onChange = { current = current.withTimeLimit(it) },
            )
            Spacer(Modifier.height(Spacing.xs))
        }
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.l, vertical = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                modifier =
                    Modifier.weight(1f)
                        .height(ButtonDefaults.MediumContainerHeight)
                        .testTag("mission_config_cancel"),
            ) {
                Text(stringResource(R.string.editor_cancel))
            }
            Button(
                onClick = {
                    feedback.confirm()
                    onConfirm(current)
                },
                shapes = ButtonDefaults.shapes(),
                modifier =
                    Modifier.weight(1f)
                        .height(ButtonDefaults.MediumContainerHeight)
                        .testTag("mission_config_ok"),
            ) {
                Text(stringResource(R.string.config_done))
            }
        }
    }
}

@Composable
private fun MissionHeader(mission: Mission) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = missionIcon(mission),
            size = 56.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(Spacing.l))
        Column {
            Text(text = missionTypeLabel(mission), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(missionDescription(mission)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimeLimitSection(seconds: Int, onChange: (Int) -> Unit) {
    val options = (TIME_LIMIT_PRESETS + seconds).distinct().sorted()
    ConfigSection(
        title = stringResource(R.string.config_time_limit),
        body = stringResource(R.string.config_time_limit_body),
    ) {
        ChoiceChips(
            options = options,
            isSelected = { it == seconds },
            label = { timeLimitLabel(it) },
            tag = { "mission_config_time_limit:$it" },
            onSelect = onChange,
        )
    }
}

@Composable
private fun timeLimitLabel(seconds: Int): String {
    val minutes = seconds / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return when {
        seconds == 0 -> stringResource(R.string.config_time_none)
        minutes == 0 -> stringResource(R.string.config_time_seconds, rest)
        rest == 0 -> stringResource(R.string.config_time_minutes, minutes)
        else -> stringResource(R.string.config_time_minutes_seconds, minutes, rest)
    }
}

/** A tonal card with a title, optional help text and the controls underneath. */
@Composable
internal fun ConfigSection(
    title: String,
    body: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(Spacing.l)
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    if (body != null) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(Spacing.m))
                    trailing()
                }
            }
            content()
        }
    }
}

/** A section whose only control is a stepper on the trailing side. */
@Composable
internal fun StepperSection(
    title: String,
    value: Int,
    tag: String,
    onChange: (Int) -> Unit,
    step: Int,
    min: Int,
    max: Int,
    body: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    ConfigSection(
        title = title,
        body = body,
        trailing = {
            Stepper(
                value = value,
                tag = tag,
                onChange = onChange,
                step = step,
                min = min,
                max = max,
            )
        },
        content = content,
    )
}

/** Wrapping filter chips; a check marks the selected ones. */
@Composable
internal fun <T> ChoiceChips(
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { option ->
            val selected = isSelected(option)
            FilterChip(
                selected = selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                leadingIcon =
                    if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                modifier = Modifier.testTag(tag(option)),
            )
        }
    }
}

/** A full-width connected button group where exactly one option is selected. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                contentPadding = PaddingValues(0.dp),
                modifier =
                    Modifier.weight(1f)
                        .height(48.dp)
                        .semantics { role = Role.RadioButton }
                        .testTag(tag(option)),
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun missionDescription(mission: Mission): Int =
    when (mission) {
        is Mission.Math -> R.string.config_desc_math
        is Mission.Memory -> R.string.config_desc_memory
        is Mission.Shake -> R.string.config_desc_shake
        is Mission.Step -> R.string.config_desc_step
        is Mission.Squat -> R.string.config_desc_squat
        is Mission.Typing -> R.string.config_desc_typing
        is Mission.Barcode -> R.string.config_desc_barcode
        is Mission.Photo -> R.string.config_desc_photo
        is Mission.ImageRecognition -> R.string.config_desc_image
        is Mission.WordBeat -> R.string.config_desc_word_beat
    }

private fun Mission.withTimeLimit(seconds: Int): Mission =
    when (this) {
        is Mission.Math -> copy(timeLimitSeconds = seconds)
        is Mission.Memory -> copy(timeLimitSeconds = seconds)
        is Mission.Shake -> copy(timeLimitSeconds = seconds)
        is Mission.Step -> copy(timeLimitSeconds = seconds)
        is Mission.Squat -> copy(timeLimitSeconds = seconds)
        is Mission.Typing -> copy(timeLimitSeconds = seconds)
        is Mission.Barcode -> copy(timeLimitSeconds = seconds)
        is Mission.Photo -> copy(timeLimitSeconds = seconds)
        is Mission.ImageRecognition -> copy(timeLimitSeconds = seconds)
        is Mission.WordBeat -> copy(timeLimitSeconds = seconds)
    }

private val TIME_LIMIT_PRESETS = listOf(0, 30, 60, 120, 180, 300, 600)
private const val SECONDS_PER_MINUTE = 60
