package dev.tomng.alarme.core.alarmengine.poweroff

/** The global actions the blocker can press; mirrors the accessibility constants. */
enum class GlobalAction {
    BACK,
    HOME,
}

/** One press of the block sequence and how long to wait before the next one. */
data class BlockStep(val action: GlobalAction, val pauseAfterMillis: Long)

/** Everything the decision depends on, captured at the moment the ring window loses focus. */
data class BlockerInputs(
    val enabled: Boolean,
    val dismissed: Boolean,
    val screenKeeperPaused: Boolean,
    val windowFocused: Boolean,
)

sealed interface BlockerDecision {
    /** Nothing to do: the toggle is off, the user is dismissing, or we are already in front. */
    data object Idle : BlockerDecision

    /** The user left the ring screen for another app: pull them back. */
    data object Block : BlockerDecision
}

/**
 * Decides whether a focus loss during a ring is a cheat attempt worth fighting. Pure so the policy
 * is testable without an accessibility service: the service only executes the decision.
 */
object PowerOffBlocker {

    fun decide(inputs: BlockerInputs): BlockerDecision =
        when {
            !inputs.enabled -> BlockerDecision.Idle
            inputs.dismissed -> BlockerDecision.Idle
            inputs.screenKeeperPaused -> BlockerDecision.Idle
            inputs.windowFocused -> BlockerDecision.Idle
            else -> BlockerDecision.Block
        }

    /**
     * BACK then HOME, three times, 100 ms apart: BACK closes whatever dialog or app the user opened
     * (including the power menu on most skins), HOME returns to the launcher, and the ring screen's
     * own watchdog brings the alarm back to the front.
     */
    fun blockSequence(): List<BlockStep> =
        List(REPEATS) { listOf(GlobalAction.BACK, GlobalAction.HOME) }
            .flatten()
            .mapIndexed { index, action ->
                BlockStep(
                    action = action,
                    pauseAfterMillis = if (index == REPEATS * 2 - 1) 0L else PAUSE_MILLIS,
                )
            }

    /**
     * The service only fights when the window in front is somebody else's: a system dialog over our
     * own screen (a permission prompt, say) is not a cheat attempt.
     */
    fun shouldBlockForeground(foregroundPackage: String?, ownPackage: String): Boolean =
        foregroundPackage != null && foregroundPackage != ownPackage

    private const val REPEATS = 3
    private const val PAUSE_MILLIS = 100L
}
