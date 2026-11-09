package dev.tomng.alarme

import androidx.compose.ui.test.assertIsDisplayed
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
 * walks the two screens every user meets, with Android's accessibility test framework checking the
 * rendered hierarchy as it goes.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun enableAccessibilityChecks() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
    }

    @Test
    fun theAlarmListShowsItsActions() {
        compose.onNodeWithTag("alarmlist:new").assertIsDisplayed()
        compose.onNodeWithTag("alarmlist:settings").assertIsDisplayed()
    }

    @Test
    fun theNewAlarmButtonOpensTheEditor() {
        compose.onNodeWithTag("alarmlist:new").performClick()

        compose.onNodeWithTag("editor_screen").assertIsDisplayed()
        compose.onNodeWithTag("editor_save").assertIsDisplayed()
    }

    @Test
    fun theEditorSavesAndReturnsToTheList() {
        compose.onNodeWithTag("alarmlist:new").performClick()
        compose.onNodeWithTag("editor_save").performClick()

        compose.onNodeWithTag("alarmlist:new").assertIsDisplayed()
    }
}
