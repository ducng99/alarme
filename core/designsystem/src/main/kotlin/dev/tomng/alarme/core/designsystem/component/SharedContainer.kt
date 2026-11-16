package dev.tomng.alarme.core.designsystem.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/** The app shell's shared-transition scope, absent in tests and previews. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The navigation transition the current destination is animating in. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks this node as one end of a container transform keyed by [key]: a list card and the screen it
 * opens share the key, and navigation morphs one into the other. A no-op outside the shell.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedContainer(key: Any, shape: Shape = MaterialTheme.shapes.extraLarge): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    val spec = MaterialTheme.motionScheme
    return with(shared) {
        this@sharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            enter = fadeIn(spec.defaultEffectsSpec()),
            exit = fadeOut(spec.fastEffectsSpec()),
            resizeMode = RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/** The key that morphs an alarm card (or the new-alarm button, for id 0) into its editor. */
fun alarmContainerKey(alarmId: Long): String = "alarm-container-$alarmId"
