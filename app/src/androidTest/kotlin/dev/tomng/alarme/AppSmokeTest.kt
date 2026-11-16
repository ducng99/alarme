package dev.tomng.alarme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's instrumented smoke test: it starts the real activity with the real dependency graph and
 * walks the screens every user meets, with Android's accessibility test framework checking the
 * rendered hierarchy as it goes.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun enableAccessibilityChecks() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
    }

    /** A fresh install opens on setup; leave it so every test starts from the list. */
    @Before
    fun leaveOnboarding() {
        compose.waitUntilAtLeastOneExists(
            hasTestTag("onboarding_screen") or hasTestTag("alarmlist:new"),
            timeoutMillis = 5_000,
        )
        if (compose.onAllNodes(hasTestTag("onboarding_skip")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("onboarding_skip").performClick()
        }
        compose.waitUntilAtLeastOneExists(hasTestTag("alarmlist:new"), timeoutMillis = 5_000)
    }

    @Test
    fun theAlarmListShowsItsActions() {
        compose.onNodeWithTag("alarmlist:new").assertIsDisplayed()
        compose.onNodeWithTag("alarmlist:quick").assertIsDisplayed()
        compose.onNodeWithTag("alarmlist:settings").assertIsDisplayed()
    }

    @Test
    fun theNewAlarmButtonOpensTheEditor() {
        compose.onNodeWithTag("alarmlist:new").performClick()

        compose.waitUntilAtLeastOneExists(hasTestTag("editor_screen"), timeoutMillis = 5_000)
        compose.onNodeWithTag("editor_hour").assertIsDisplayed()
        compose.onNodeWithTag("editor_save").assertIsDisplayed()
    }

    @Test
    fun theQuickButtonOffersPresets() {
        compose.onNodeWithTag("alarmlist:quick").performClick()

        compose.waitUntilAtLeastOneExists(hasTestTag("alarmlist:quick:60"), timeoutMillis = 5_000)
    }

    @Test
    fun systemBackLeavesSettingsForTheList() {
        compose.onNodeWithTag("alarmlist:settings").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("settings_screen"), timeoutMillis = 5_000)

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.waitUntilAtLeastOneExists(hasTestTag("alarmlist:new"), timeoutMillis = 5_000)
    }

    @Test
    fun systemBackLeavesTheEditorForTheList() {
        compose.onNodeWithTag("alarmlist:new").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("editor_screen"), timeoutMillis = 5_000)

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.waitUntilAtLeastOneExists(hasTestTag("alarmlist:new"), timeoutMillis = 5_000)
    }

    @Test
    fun theEditorSavesAndReturnsToTheList() {
        compose.onNodeWithTag("alarmlist:new").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("editor_save"), timeoutMillis = 5_000)
        compose.onNodeWithTag("editor_save").performClick()

        compose.waitUntilAtLeastOneExists(hasTestTag("alarmlist:new"), timeoutMillis = 5_000)
    }

    @Test
    fun settingsOpensTheDefaultSoundPicker() {
        compose.onNodeWithTag("alarmlist:settings").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("settings_screen"), timeoutMillis = 5_000)
        compose.onNodeWithTag("settings_default_sound").performClick()

        compose.waitUntilAtLeastOneExists(hasTestTag("ringtone:tab:0"), timeoutMillis = 5_000)
        compose.onNodeWithTag("ringtone:tab:2").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("ringtone:import"), timeoutMillis = 5_000)
    }
}
