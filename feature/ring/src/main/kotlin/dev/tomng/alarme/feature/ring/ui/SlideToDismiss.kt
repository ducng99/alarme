package dev.tomng.alarme.feature.ring.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.rememberFeedback
import dev.tomng.alarme.feature.ring.R
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** How far the thumb must travel, as a share of the track, before letting go dismisses. */
internal const val SLIDE_THRESHOLD = 0.7f

internal fun slideCompleted(offset: Float, travel: Float): Boolean =
    travel > 0f && offset / travel >= SLIDE_THRESHOLD

/**
 * Dismiss needs a deliberate slide so a sleepy palm cannot end the alarm. The track exposes a plain
 * click action, so screen readers and switch access dismiss with one activation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SlideToDismiss(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.ring_slide_to_dismiss)
    val feedback = rememberFeedback()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val springSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier =
            modifier.fillMaxWidth().height(TRACK_HEIGHT).testTag("ring:dismiss").semantics(
                mergeDescendants = true,
            ) {
                role = Role.Button
                contentDescription = label
                onClick {
                    onDismiss()
                    true
                }
            },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(THUMB_INSET)) {
            val thumbPx = with(density) { THUMB_SIZE.toPx() }
            val travel = (constraints.maxWidth - thumbPx).coerceAtLeast(0f)
            val progress = if (travel > 0f) offset.value / travel else 0f
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier.align(Alignment.Center).graphicsLayer {
                        alpha = (1f - progress).coerceIn(0f, 1f)
                    },
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier.size(THUMB_SIZE)
                        .offset {
                            IntOffset(
                                x = (if (rtl) -offset.value else offset.value).roundToInt(),
                                y = 0,
                            )
                        }
                        .pointerInput(travel) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (slideCompleted(offset.value, travel)) {
                                        feedback.confirm()
                                        scope.launch { offset.animateTo(travel, springSpec) }
                                        onDismiss()
                                    } else {
                                        armed = false
                                        scope.launch { offset.animateTo(0f, springSpec) }
                                    }
                                },
                                onDragCancel = {
                                    armed = false
                                    scope.launch { offset.animateTo(0f, springSpec) }
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                val delta = if (rtl) -dragAmount else dragAmount
                                val next = (offset.value + delta).coerceIn(0f, travel)
                                val nowArmed = slideCompleted(next, travel)
                                if (nowArmed && !armed) feedback.threshold()
                                armed = nowArmed
                                scope.launch { offset.snapTo(next) }
                            }
                        },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector =
                            if (armed) Icons.Filled.Check
                            else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(ICON_SIZE),
                    )
                }
            }
        }
    }
}

private val TRACK_HEIGHT = 88.dp
private val THUMB_INSET = 8.dp
private val THUMB_SIZE = 72.dp
private val ICON_SIZE = 32.dp
