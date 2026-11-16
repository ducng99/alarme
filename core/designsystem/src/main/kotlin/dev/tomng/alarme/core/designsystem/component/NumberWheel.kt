package dev.tomng.alarme.core.designsystem.component

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.R
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * A snapping vertical wheel over [values]. The centred row is the selection; every snap ticks a
 * haptic. With [looping] the list repeats so the user can spin past the end (minutes 59 to 00).
 * Accessibility services get increase/decrease actions instead of a fling.
 */
@Composable
fun <T> NumberWheel(
    values: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    looping: Boolean = true,
    width: Dp = 88.dp,
    itemHeight: Dp = 64.dp,
    visibleItems: Int = 3,
    textStyle: TextStyle = MaterialTheme.typography.displayMedium,
    increaseLabel: String = stringResource(R.string.ds_wheel_increase),
    decreaseLabel: String = stringResource(R.string.ds_wheel_decrease),
    testTag: String? = null,
) {
    val count = values.size
    val total = if (looping) count * LOOP_REPEATS else count
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val baseIndex = if (looping) count * (LOOP_REPEATS / 2) + selectedIndex else selectedIndex
    val state = rememberLazyListState(initialFirstVisibleItemIndex = baseIndex)
    val scope = rememberCoroutineScope()
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val centred by remember(state) { derivedStateOf { centredIndex(state) } }

    LaunchedEffect(state, count) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    val value = values[centredIndex(state) % count]
                    if (value != currentSelected) currentOnSelect(value)
                }
            }
    }
    LaunchedEffect(selected) {
        if (!state.isScrollInProgress && values[centredIndex(state) % count] != selected) {
            val current = centredIndex(state)
            val delta = (selectedIndex - current % count)
            state.animateScrollToItem((current + delta).coerceIn(0, total - 1))
        }
    }

    val pad = visibleItems / 2
    val step: (Int) -> Unit = { by ->
        val next =
            (selectedIndex + by).let {
                if (looping) (it + count) % count else it.coerceIn(0, count - 1)
            }
        onSelect(values[next])
        scope.launch {
            state.animateScrollToItem((centredIndex(state) + by).coerceIn(0, total - 1))
        }
    }
    Box(
        modifier =
            modifier.width(width).height(itemHeight * visibleItems).clearAndSetSemantics {
                if (testTag != null) this.testTag = testTag
                this.contentDescription = contentDescription
                stateDescription = label(selected)
                customActions =
                    listOf(
                        CustomAccessibilityAction(increaseLabel) {
                            step(1)
                            true
                        },
                        CustomAccessibilityAction(decreaseLabel) {
                            step(-1)
                            true
                        },
                    )
            },
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().height(itemHeight * visibleItems),
        ) {
            items(count = total + pad * 2) { raw ->
                val index = raw - pad
                Box(
                    modifier =
                        Modifier.fillMaxWidth().height(itemHeight).graphicsLayer {
                            val distance = abs(index - centred).toFloat()
                            val fade = (1f - distance * FADE_PER_ROW).coerceIn(MIN_ALPHA, 1f)
                            alpha = fade
                            val scale = 1f - distance * SCALE_PER_ROW
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (index in 0 until total) {
                        Text(
                            text = label(values[index % count]),
                            style = textStyle,
                            color = LocalContentColor.current,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** The index of the value row currently under the centre line (padding rows excluded). */
private fun centredIndex(state: LazyListState): Int {
    val info = state.layoutInfo
    val firstHeight =
        info.visibleItemsInfo.firstOrNull()?.size ?: return state.firstVisibleItemIndex
    if (firstHeight == 0) return state.firstVisibleItemIndex
    val offsetRows = (state.firstVisibleItemScrollOffset + firstHeight / 2) / firstHeight
    return state.firstVisibleItemIndex + offsetRows
}

private const val LOOP_REPEATS = 200
private const val FADE_PER_ROW = 0.55f
private const val MIN_ALPHA = 0.15f
private const val SCALE_PER_ROW = 0.18f
