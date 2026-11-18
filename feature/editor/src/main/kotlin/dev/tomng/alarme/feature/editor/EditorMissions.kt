package dev.tomng.alarme.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import dev.tomng.alarme.core.designsystem.component.IconBadge
import dev.tomng.alarme.core.designsystem.component.NavRow
import dev.tomng.alarme.core.designsystem.component.SegmentedGroup
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.MissionType

/** The label with its icon: the emoji button opens the emoji picker in a bottom sheet. */
@Composable
internal fun LabelGroup(draft: AlarmDraft, callbacks: AlarmEditorCallbacks) {
    var picking by remember { mutableStateOf(false) }
    SegmentedGroup {
        row { shapes ->
            TextRow(
                shapes = shapes,
                value = draft.name,
                onValueChange = callbacks.onName,
                placeholder = stringResource(R.string.editor_name),
                icon = null,
                tag = "editor_name",
                leading = {
                    val label = stringResource(R.string.editor_emoji)
                    Surface(
                        onClick = { picking = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier =
                            Modifier.size(40.dp).testTag("editor_emoji").semantics {
                                contentDescription = label
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(draft.emoji, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                },
            )
        }
    }
    if (picking) {
        EmojiPickerSheet(
            onPick = {
                callbacks.onEmoji(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPickerSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("editor_emoji_picker"),
    ) {
        AndroidView(
            factory = { context ->
                EmojiPickerView(context).apply { setOnEmojiPickedListener { onPick(it.emoji) } }
            },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        )
    }
}

/**
 * The mission chain: each row opens its configuration, can be moved up or down, and removed (with
 * undo from the snackbar). The last row adds a mission.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MissionGroup(
    draft: AlarmDraft,
    highlightIncomplete: Boolean,
    onMoveMission: (from: Int, to: Int) -> Unit,
    onRemoveMission: (Int) -> Unit,
    onEditMission: (Int) -> Unit,
    onAddMission: () -> Unit,
) {
    SegmentedGroup {
        draft.missions.forEachIndexed { index, mission ->
            row { shapes ->
                val incomplete = !mission.isComplete()
                val moveUp = stringResource(R.string.editor_mission_move_up)
                val moveDown = stringResource(R.string.editor_mission_move_down)
                SegmentedListItem(
                    onClick = { onEditMission(index) },
                    shapes = shapes,
                    modifier =
                        Modifier.testTag("editor_mission:$index").semantics {
                            customActions = buildList {
                                if (index > 0) {
                                    add(
                                        CustomAccessibilityAction(moveUp) {
                                            onMoveMission(index, index - 1)
                                            true
                                        },
                                    )
                                }
                                if (index < draft.missions.lastIndex) {
                                    add(
                                        CustomAccessibilityAction(moveDown) {
                                            onMoveMission(index, index + 1)
                                            true
                                        },
                                    )
                                }
                            }
                        },
                    leadingContent = {
                        if (incomplete && highlightIncomplete) {
                            IconBadge(
                                Icons.Outlined.ErrorOutline,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        } else {
                            IconBadge(missionIcon(mission))
                        }
                    },
                    supportingContent = {
                        Text(
                            text =
                                if (incomplete) stringResource(R.string.editor_mission_incomplete)
                                else missionSummary(mission),
                            color =
                                if (incomplete) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Row {
                            if (draft.missions.size > 1) {
                                IconButton(
                                    onClick = {
                                        onMoveMission(
                                            index,
                                            if (index > 0) index - 1 else index + 1,
                                        )
                                    },
                                    modifier = Modifier.testTag("editor_mission_move:$index"),
                                ) {
                                    Icon(
                                        imageVector =
                                            if (index > 0) Icons.Outlined.KeyboardArrowUp
                                            else Icons.Outlined.KeyboardArrowDown,
                                        contentDescription = if (index > 0) moveUp else moveDown,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onRemoveMission(index) },
                                modifier = Modifier.testTag("editor_mission_remove:$index"),
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription =
                                        stringResource(R.string.editor_mission_remove),
                                )
                            }
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            R.string.editor_mission_slot,
                            index + 1,
                            missionTypeLabel(mission),
                        ),
                    )
                }
            }
        }
        row { shapes ->
            NavRow(
                shapes = shapes,
                title = stringResource(R.string.editor_add_mission),
                supporting =
                    if (draft.missions.isEmpty()) stringResource(R.string.editor_missions_empty)
                    else null,
                icon = Icons.Outlined.Add,
                enabled = draft.canAddMission,
                onClick = onAddMission,
                modifier = Modifier.testTag("editor_add_mission"),
            )
        }
    }
}

/** A grid of mission types; picking one opens its configuration in the same sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddMissionSheet(onDismiss: () -> Unit, onAdd: (Mission) -> Unit) {
    var configuring by remember { mutableStateOf<Mission?>(null) }
    val current = configuring
    if (current != null) {
        MissionConfigSheet(
            mission = current,
            onDismiss = { configuring = null },
            onConfirm = onAdd,
        )
        return
    }
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("editor_mission_types"),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = Spacing.l)) {
            Text(
                stringResource(R.string.editor_add_mission_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.editor_add_mission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.l),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(bottom = Spacing.l),
            ) {
                items(MissionType.entries) { type ->
                    MissionTypeTile(type) { configuring = MissionDefaults.of(type) }
                }
            }
        }
    }
}

@Composable
private fun MissionTypeTile(type: MissionType, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 96.dp)
                .testTag("editor_mission_type:${type.name}"),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            IconBadge(missionIcon(type), size = 48.dp)
            Text(
                missionTypeLabel(type),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
