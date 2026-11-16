package dev.tomng.alarme.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Semantic haptics, kept for moments that matter: completions, errors and gesture thresholds. */
@Stable
class Feedback(private val haptics: HapticFeedback) {
    fun confirm() = haptics.performHapticFeedback(HapticFeedbackType.Confirm)

    fun reject() = haptics.performHapticFeedback(HapticFeedbackType.Reject)

    fun threshold() = haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

    fun longPress() = haptics.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberFeedback(): Feedback {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { Feedback(haptics) }
}
