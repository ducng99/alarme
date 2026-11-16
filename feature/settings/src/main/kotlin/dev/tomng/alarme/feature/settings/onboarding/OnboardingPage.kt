package dev.tomng.alarme.feature.settings.onboarding

import dev.tomng.alarme.feature.settings.permissions.PermissionCenterState
import dev.tomng.alarme.feature.settings.permissions.PermissionItem

/** One page of the setup walk-through. */
internal sealed interface OnboardingPage {
    data object Welcome : OnboardingPage

    data class Grant(val item: PermissionItem) : OnboardingPage

    data object Ready : OnboardingPage
}

/** The pages to show: the grants still missing when setup opened, framed by welcome and ready. */
internal fun onboardingPages(
    permissions: PermissionCenterState,
    welcome: Boolean,
): List<OnboardingPage> = buildList {
    if (welcome) add(OnboardingPage.Welcome)
    permissions.onboardingSteps().forEach { add(OnboardingPage.Grant(it)) }
    add(OnboardingPage.Ready)
}
