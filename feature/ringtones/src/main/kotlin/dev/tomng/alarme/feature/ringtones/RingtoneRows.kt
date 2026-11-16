package dev.tomng.alarme.feature.ringtones

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind

/** Silent or shuffle: a choice without audio to audition. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PseudoRow(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        colors = pickerColors(),
        leadingContent = { RowBadge(icon, selected) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { SelectionMark(selected = selected, playing = false, rowId = null) },
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One sound. Tapping picks it and auditions it; tapping it again while it is picked stops or
 * replays the audition. [onDelete] adds a delete button for the user's own sounds.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SoundRow(
    row: CatalogRingtone,
    icon: ImageVector,
    selected: Boolean,
    playing: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val playingLabel = stringResource(R.string.ringtone_playing)
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        modifier =
            modifier.testTag("ringtone:row:${row.id}").semantics {
                if (playing) stateDescription = playingLabel
            },
        colors = pickerColors(),
        leadingContent = { RowBadge(icon, selected) },
        supportingContent =
            if (row.kind == RingtoneKind.RECORD) {
                { Text(stringResource(R.string.ringtone_kind_recording)) }
            } else {
                null
            },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("ringtone:delete:${row.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription =
                                stringResource(R.string.ringtone_delete_named, row.title),
                        )
                    }
                }
                SelectionMark(selected = selected, playing = playing, rowId = row.id)
            }
        },
    ) {
        Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun pickerColors() =
    ListItemDefaults.segmentedColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

@Composable
private fun RowBadge(icon: ImageVector, selected: Boolean) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
    )
}

/** Nothing, a check, or a moving equaliser while the picked sound plays. */
@Composable
private fun SelectionMark(selected: Boolean, playing: Boolean, rowId: Long?) {
    val state =
        when {
            playing -> MarkState.Playing
            selected -> MarkState.Selected
            else -> MarkState.None
        }
    val motion = MaterialTheme.motionScheme
    val enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec())
    val exit = fadeOut(motion.fastEffectsSpec())
    AnimatedContent(
        targetState = state,
        transitionSpec = { enter togetherWith exit },
        contentAlignment = Alignment.Center,
        label = "selectionMark",
        modifier = Modifier.size(MARK_SIZE),
    ) { shown ->
        when (shown) {
            MarkState.Playing ->
                Equalizer(
                    modifier =
                        if (rowId != null) Modifier.testTag("ringtone:playing:$rowId")
                        else Modifier,
                )
            MarkState.Selected ->
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            MarkState.None -> Box(Modifier)
        }
    }
}

private enum class MarkState {
    None,
    Selected,
    Playing,
}

@Composable
private fun Equalizer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bars = EQUALIZER_PERIODS.map { period ->
        transition.animateFloat(
            initialValue = EQUALIZER_MIN,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(period), RepeatMode.Reverse),
            label = "bar",
        )
    }
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.size(EQUALIZER_SIZE),
        horizontalArrangement = Arrangement.spacedBy(EQUALIZER_GAP),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            val height by bar
            Box(
                modifier =
                    Modifier.width(EQUALIZER_BAR)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleY = height
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .clip(CircleShape)
                        .background(color),
            )
        }
    }
}

/** Import from files and record a memo, side by side. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AddSoundButtons(
    recording: Boolean,
    onImport: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FilledTonalButton(
            onClick = onImport,
            shapes = ButtonDefaults.shapes(),
            enabled = !recording,
            modifier =
                Modifier.weight(1f)
                    .heightIn(min = ButtonDefaults.MediumContainerHeight)
                    .testTag("ringtone:import"),
        ) {
            Icon(
                Icons.Outlined.FileUpload,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Box(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                stringResource(R.string.ringtone_import),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onRecord,
            shapes = ButtonDefaults.shapes(),
            colors =
                if (recording) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            modifier =
                Modifier.weight(1f)
                    .heightIn(min = ButtonDefaults.MediumContainerHeight)
                    .testTag("ringtone:record"),
        ) {
            Icon(
                imageVector = if (recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Box(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text =
                    stringResource(
                        if (recording) R.string.ringtone_record_stop else R.string.ringtone_record,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The scaffold padding without its top, for content laid out below the tab row. */
@Stable
internal class BottomPadding(private val inner: PaddingValues) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        inner.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        inner.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding(): Dp = inner.calculateBottomPadding()
}

private val MARK_SIZE = 24.dp
private val EQUALIZER_SIZE = 18.dp
private val EQUALIZER_BAR = 4.dp
private val EQUALIZER_GAP = 3.dp
private const val EQUALIZER_MIN = 0.25f
private val EQUALIZER_PERIODS = listOf(420, 300, 520)
