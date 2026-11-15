package dev.tomng.alarme

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Renders [screen] with Material shared-axis transitions: deeper screens slide in from the end,
 * going back slides them out. While a system back gesture is in progress the current screen shrinks
 * and follows the finger (predictive back); committing navigates to [parent].
 *
 * Every destination gets its own [ViewModelStore], cleared when the destination leaves, so opening
 * the editor twice starts from a fresh ViewModel instead of the previous session's state.
 */
@Composable
internal fun ScreenHost(
    screen: Screen,
    parent: Screen?,
    onBack: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Screen) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val edge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val stateHolder = rememberSaveableStateHolder()
    val activityOwner = checkNotNull(LocalViewModelStoreOwner.current)

    PredictiveBackHandler(enabled = parent != null) { events: Flow<BackEventCompat> ->
        try {
            events.collect { event ->
                edge.snapTo(if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f)
                progress.snapTo(event.progress)
            }
            parent?.let(onBack)
            scope.launch { progress.snapTo(0f) }
        } catch (cancelled: CancellationException) {
            scope.launch { progress.animateTo(0f) }
            throw cancelled
        }
    }

    AnimatedContent(
        targetState = screen,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { sharedAxis(forward = depth(targetState) >= depth(initialState)) },
        contentKey = { it.key },
        label = "screens",
    ) { target ->
        val owner = rememberScreenOwner(target.key, activityOwner)
        val isCurrent = target == screen
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        if (isCurrent && progress.value > 0f) {
                            val p = progress.value
                            val scale = 1f - PREDICTIVE_SCALE * p
                            scaleX = scale
                            scaleY = scale
                            translationX = edge.value * PREDICTIVE_SHIFT.toPx() * p
                            transformOrigin = TransformOrigin.Center
                        }
                    }
                    .clip(
                        if (isCurrent && progress.value > 0f) MaterialTheme.shapes.extraLarge
                        else RectangleShape,
                    ),
        ) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                stateHolder.SaveableStateProvider(target.key) { content(target) }
            }
        }
        DisposableEffect(target.key) {
            onDispose {
                if (target.key != screen.key && owner !== activityOwner) {
                    owner.viewModelStore.clear()
                    stateHolder.removeState(target.key)
                }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<Screen>.sharedAxis(forward: Boolean): ContentTransform {
    val duration = 400
    val enter =
        slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { width ->
            if (forward) width / SLIDE_FRACTION else -width / SLIDE_FRACTION
        } +
            fadeIn(tween(duration / 2, delayMillis = duration / 4)) +
            scaleIn(tween(duration), initialScale = if (forward) 1f else ENTER_SCALE)
    val exit =
        slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { width ->
            if (forward) -width / SLIDE_FRACTION else width / SLIDE_FRACTION
        } +
            fadeOut(tween(duration / 3)) +
            scaleOut(tween(duration), targetScale = if (forward) ENTER_SCALE else 1f)
    return (enter togetherWith exit).apply { targetContentZIndex = if (forward) 1f else 0f }
}

/** How deep [screen] sits below the list; used to pick the transition direction. */
private fun depth(screen: Screen): Int {
    var depth = 0
    var current: Screen? = parentScreen(screen)
    while (current != null) {
        depth++
        current = parentScreen(current)
    }
    return depth
}

/** A stable identity for a destination: two editor sessions for the same alarm share it. */
private val Screen.key: String
    get() =
        when (this) {
            Screen.List -> "list"
            Screen.Settings -> "settings"
            Screen.Diagnostics -> "diagnostics"
            Screen.Optimize -> "optimize"
            Screen.Backup -> "backup"
            is Screen.Editor -> "editor:$alarmId"
            is Screen.Picker ->
                when (val t = target) {
                    PickerTarget.Default -> "picker:default"
                    is PickerTarget.ForAlarm -> "picker:${t.alarm.id}"
                }
        }

@Composable
private fun rememberScreenOwner(key: String, parent: ViewModelStoreOwner): ViewModelStoreOwner =
    remember(key) {
        val defaults = parent as? HasDefaultViewModelProviderFactory
        if (key == "list") parent
        else if (defaults == null) {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        } else {
            object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
                override val viewModelStore = ViewModelStore()
                override val defaultViewModelProviderFactory: ViewModelProvider.Factory
                    get() = defaults.defaultViewModelProviderFactory

                override val defaultViewModelCreationExtras: CreationExtras
                    get() = defaults.defaultViewModelCreationExtras
            }
        }
    }

private const val SLIDE_FRACTION = 5
private const val ENTER_SCALE = 0.94f
private const val PREDICTIVE_SCALE = 0.1f
private val PREDICTIVE_SHIFT = 16.dp
