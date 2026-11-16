package dev.tomng.alarme.feature.settings.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.feature.settings.permissions.PermissionCenterState
import dev.tomng.alarme.feature.settings.permissions.PermissionItem
import dev.tomng.alarme.feature.settings.permissions.PermissionRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun state(vararg granted: PermissionItem) =
        PermissionCenterState(
            rows = PermissionItem.entries.map { PermissionRow(it, it in granted) },
        )

    @Test
    fun `pages cover only the missing grants between welcome and ready`() {
        val pages =
            onboardingPages(
                state(PermissionItem.NOTIFICATIONS, PermissionItem.BATTERY),
                welcome = true,
            )

        assertThat(pages)
            .containsExactly(
                OnboardingPage.Welcome,
                OnboardingPage.Grant(PermissionItem.EXACT_ALARM),
                OnboardingPage.Grant(PermissionItem.FULL_SCREEN_INTENT),
                OnboardingPage.Ready,
            )
            .inOrder()
    }

    @Test
    fun `allow asks for the grant of the current page`() {
        val asked = mutableListOf<PermissionItem>()
        compose.setContent {
            OnboardingScreen(
                permissions = state(),
                welcome = false,
                onAllow = { asked += it },
                onFinish = {},
            )
        }

        compose.onNodeWithTag("onboarding_step_NOTIFICATIONS").assertIsDisplayed()
        compose.onNodeWithTag("onboarding_primary").performClick()

        assertThat(asked).containsExactly(PermissionItem.NOTIFICATIONS)
    }

    @Test
    fun `a grant that arrives moves on to the next page`() {
        var permissions by mutableStateOf(state())
        compose.setContent {
            OnboardingScreen(
                permissions = permissions,
                welcome = false,
                onAllow = {},
                onFinish = {},
            )
        }

        compose.onNodeWithTag("onboarding_step_NOTIFICATIONS").assertIsDisplayed()
        permissions = state(PermissionItem.NOTIFICATIONS)

        compose.onNodeWithText("Continue").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose
                .onAllNodesWithTag("onboarding_step_EXACT_ALARM")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("onboarding_step_EXACT_ALARM").assertIsDisplayed()
    }

    @Test
    fun `not now skips a page and the last page finishes`() {
        var finished = false
        compose.setContent {
            OnboardingScreen(
                permissions =
                    state(
                        PermissionItem.NOTIFICATIONS,
                        PermissionItem.EXACT_ALARM,
                        PermissionItem.FULL_SCREEN_INTENT,
                    ),
                welcome = false,
                onAllow = {},
                onFinish = { finished = true },
            )
        }

        compose.onNodeWithTag("onboarding_skip").performClick()
        compose.onNodeWithText("You're all set").assertIsDisplayed()
        compose.onNodeWithTag("onboarding_primary").performClick()

        assertThat(finished).isTrue()
    }
}
