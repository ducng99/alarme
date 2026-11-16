package dev.tomng.alarme.feature.alarmlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.tomng.alarme.core.designsystem.component.NumberWheel
import dev.tomng.alarme.core.designsystem.component.rememberFeedback
import dev.tomng.alarme.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch

/** A bottom sheet for a one-off alarm counted from now: presets, or any duration on two wheels. */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
internal fun QuickAlarmSheet(onDismiss: () -> Unit, onCreate: (minutes: Int) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val feedback = rememberFeedback()
    var custom by remember { mutableStateOf(false) }
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(DEFAULT_CUSTOM_MINUTES) }
    val create: (Int) -> Unit = { total ->
        feedback.confirm()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onCreate(total) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.l)
                    .testTag("alarmlist:quick_sheet"),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column {
                Text(
                    stringResource(R.string.alarmlist_quick_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.alarmlist_quick_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedContent(targetState = custom, label = "quickMode") { showWheels ->
                if (showWheels) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NumberWheel(
                            values = (0..MAX_HOURS).toList(),
                            selected = hours,
                            onSelect = { hours = it },
                            label = { it.toString() },
                            contentDescription = stringResource(R.string.alarmlist_quick_hours),
                            looping = false,
                            testTag = "alarmlist:quick_hours",
                        )
                        Text(
                            stringResource(R.string.alarmlist_quick_hours),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        NumberWheel(
                            values = (0 until MINUTES_PER_HOUR).toList(),
                            selected = minutes,
                            onSelect = { minutes = it },
                            label = { "%02d".format(it) },
                            contentDescription = stringResource(R.string.alarmlist_quick_minutes),
                            testTag = "alarmlist:quick_minutes",
                        )
                        Text(
                            stringResource(R.string.alarmlist_quick_minutes),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        maxItemsInEachRow = PRESETS_PER_ROW,
                    ) {
                        PRESETS.forEach { preset ->
                            FilledTonalButton(
                                onClick = { create(preset) },
                                shapes = ButtonDefaults.shapes(),
                                modifier =
                                    Modifier.weight(1f)
                                        .height(ButtonDefaults.MediumContainerHeight)
                                        .testTag("alarmlist:quick:$preset"),
                            ) {
                                Text(
                                    presetLabel(preset),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
            ) {
                if (custom) {
                    TextButton(onClick = { custom = false }) {
                        Text(stringResource(R.string.alarmlist_quick_cancel))
                    }
                    Button(
                        onClick = { create(hours * MINUTES_PER_HOUR + minutes) },
                        enabled = hours * MINUTES_PER_HOUR + minutes > 0,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.testTag("alarmlist:quick_set"),
                    ) {
                        Text(stringResource(R.string.alarmlist_quick_set))
                    }
                } else {
                    TextButton(
                        onClick = { custom = true },
                        modifier = Modifier.testTag("alarmlist:quick_custom"),
                    ) {
                        Text(stringResource(R.string.alarmlist_quick_custom))
                    }
                }
            }
        }
    }
}

@Composable
private fun presetLabel(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> stringResource(R.string.alarmlist_quick_minutes_short, rest)
        rest == 0 -> stringResource(R.string.alarmlist_quick_hours_short, hours)
        else -> stringResource(R.string.alarmlist_quick_hours_minutes, hours, rest)
    }
}

private val PRESETS = listOf(10, 20, 30, 60, 90, 120, 360, 480, 540)
private const val PRESETS_PER_ROW = 3
private const val MINUTES_PER_HOUR = 60
private const val MAX_HOURS = 23
private const val DEFAULT_CUSTOM_MINUTES = 15
