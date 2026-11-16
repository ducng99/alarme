package dev.tomng.alarme

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
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
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tomng.alarme.core.designsystem.component.LocalNavAnimatedScope
import dev.tomng.alarme.core.designsystem.component.LocalSharedTransitionScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * Renders the top of [nav] with Material motion: shared-axis for drilling in and out, a container
 * transform between the list and the editor, and predictive back that scrubs the real transition to
 * the previous screen while the gesture is in progress.
 *
 * Every stack entry owns its [ViewModelStore] and saved state until it leaves the stack, so a
 * picker opened above the editor does not wipe the editor's draft. [paneKey] lets several stack
 * entries share one rendered pane (the list-detail layout on wide screens).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ScreenHost(
    nav: NavState,
    modifier: Modifier = Modifier,
    containerTransforms: Boolean = true,
    paneKey: (Screen) -> String = { it.key },
    content: @Composable (Screen) -> Unit,
) {
    val transitionState = remember { SeekableTransitionState(nav.current) }
    val transition = rememberTransition(transitionState, label = "screens")
    val scope = rememberCoroutineScope()
    val stateHolder = rememberSaveableStateHolder()
    val entries = rememberEntryStores()
    val depths = remember { mutableMapOf<String, Int>() }
    val motion = MaterialTheme.motionScheme

    SideEffect { nav.stack.forEachIndexed { index, screen -> depths[screen.key] = index } }

    // Once a transition settles, entries that left the stack drop their ViewModels and state.
    val stackKeys = nav.stack.flatMap { listOf(it.key, paneKey(it)) }.distinct()
    LaunchedEffect(transitionState.currentState, stackKeys) {
        if (transitionState.currentState == transitionState.targetState) {
            entries.retain(stackKeys).forEach(stateHolder::removeState)
        }
    }

    LaunchedEffect(nav.current) {
        if (
            transitionState.currentState != nav.current ||
                transitionState.targetState != nav.current
        ) {
            transitionState.animateTo(nav.current)
        }
    }

    PredictiveBackHandler(enabled = nav.canPop) { events ->
        val previous = nav.previous ?: return@PredictiveBackHandler
        try {
            events.collect { event -> transitionState.seekTo(event.progress, previous) }
            nav.pop()
        } catch (cancelled: CancellationException) {
            scope.launch { transitionState.animateTo(nav.current) }
            throw cancelled
        }
    }

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        transition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val forward = (depths[targetState.key] ?: 0) >= (depths[initialState.key] ?: 0)
                screenTransition(forward, motion, containerTransforms)
            },
            contentKey = paneKey,
        ) { target ->
            val owner = entries.owner(paneKey(target))
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner,
                LocalEntryStores provides entries,
                LocalSharedTransitionScope provides
                    if (containerTransforms) this@SharedTransitionLayout else null,
                LocalNavAnimatedScope provides this@AnimatedContent,
            ) {
                Box(Modifier.fillMaxSize()) {
                    stateHolder.SaveableStateProvider(paneKey(target)) { content(target) }
                }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<Screen>.screenTransition(
    forward: Boolean,
    motion: MotionScheme,
    containerTransforms: Boolean,
): ContentTransform {
    if (containerTransforms && isContainerTransform(initialState, targetState)) {
        return (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
            .apply { targetContentZIndex = if (forward) 1f else 0f }
    }
    val slide = motion.defaultSpatialSpec<IntOffset>()
    val enter =
        slideInHorizontally(slide) { width ->
            if (forward) width / SLIDE_FRACTION else -width / SLIDE_FRACTION
        } +
            fadeIn(motion.defaultEffectsSpec()) +
            scaleIn(motion.defaultSpatialSpec(), initialScale = if (forward) 1f else BACK_SCALE)
    val exit =
        slideOutHorizontally(slide) { width ->
            if (forward) -width / SLIDE_FRACTION else width / SLIDE_FRACTION
        } +
            fadeOut(motion.fastEffectsSpec()) +
            scaleOut(motion.defaultSpatialSpec(), targetScale = if (forward) 1f else BACK_SCALE)
    return (enter togetherWith exit).apply { targetContentZIndex = if (forward) 1f else 0f }
}

/** The list card (or the new-alarm button) morphs into the editor and back. */
private fun isContainerTransform(from: Screen, to: Screen): Boolean =
    (from == Screen.List && to is Screen.Editor) || (from is Screen.Editor && to == Screen.List)

/** Holds one [ViewModelStore] per stack entry; lives as long as the activity, across rotation. */
internal class EntryStoresViewModel : ViewModel() {
    val stores = mutableMapOf<String, ViewModelStore>()

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

/** Hands out ViewModel owners for stack entries, backed by [EntryStoresViewModel]. */
internal class EntryStores(
    private val holder: EntryStoresViewModel,
    private val parent: ViewModelStoreOwner,
) {
    fun owner(key: String): ViewModelStoreOwner {
        val store = holder.stores.getOrPut(key) { ViewModelStore() }
        val defaults = parent as? HasDefaultViewModelProviderFactory
        return if (defaults == null) {
            object : ViewModelStoreOwner {
                override val viewModelStore = store
            }
        } else {
            object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
                override val viewModelStore = store
                override val defaultViewModelProviderFactory: ViewModelProvider.Factory
                    get() = defaults.defaultViewModelProviderFactory

                override val defaultViewModelCreationExtras: CreationExtras
                    get() = defaults.defaultViewModelCreationExtras
            }
        }
    }

    /** Clears every entry whose key is not in [keys] and returns the removed keys. */
    fun retain(keys: List<String>): List<String> {
        val gone = holder.stores.keys.filter { it !in keys }
        gone.forEach { holder.stores.remove(it)?.clear() }
        return gone
    }
}

internal val LocalEntryStores = staticCompositionLocalOf<EntryStores?> { null }

@Composable
private fun rememberEntryStores(): EntryStores {
    val parent = checkNotNull(LocalViewModelStoreOwner.current)
    val holder: EntryStoresViewModel = viewModel(viewModelStoreOwner = parent)
    return remember(holder, parent) { EntryStores(holder, parent) }
}

private const val SLIDE_FRACTION = 4
private const val BACK_SCALE = 0.92f
