package dev.tomng.alarme.feature.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class BackupScreenTest {

    @get:Rule val compose = createComposeRule()

    private var exports = 0
    private var imports = 0

    private fun setScreen(state: BackupUiState) {
        compose.setContent {
            BackupScreen(
                state = state,
                onExport = { exports++ },
                onImport = { imports++ },
                onBack = {},
            )
        }
    }

    @Test
    fun `both actions are offered`() {
        setScreen(BackupUiState.Idle)

        compose.onNodeWithTag("backup_export").assertIsDisplayed().performClick()
        compose.onNodeWithTag("backup_import").assertIsDisplayed().performClick()
        compose.onNodeWithTag("backup_import_confirm").performClick()

        assertThat(exports).isEqualTo(1)
        assertThat(imports).isEqualTo(1)
    }

    @Test
    fun `restore asks before replacing the alarm list`() {
        setScreen(BackupUiState.Idle)

        compose.onNodeWithTag("backup_import").performClick()
        compose.onNodeWithText("Replace your alarms?").assertIsDisplayed()
        compose.onNodeWithTag("backup_import_cancel").performClick()

        assertThat(imports).isEqualTo(0)
        compose.onNodeWithText("Replace your alarms?").assertDoesNotExist()
    }

    @Test
    fun `working disables the actions and shows progress`() {
        setScreen(BackupUiState.Working)

        compose.onNodeWithTag("backup_export").assertIsNotEnabled()
        compose.onNodeWithTag("backup_import").assertIsNotEnabled()
        compose.onNodeWithTag("backup_progress").assertIsDisplayed()
    }

    @Test
    fun `export result shows counts`() {
        setScreen(BackupUiState.Exported(alarmCount = 4, audioFileCount = 2))

        compose.onNodeWithTag("backup_result").assertIsDisplayed()
        compose.onNodeWithText("Exported 4 alarms and 2 sounds.").assertIsDisplayed()
    }

    @Test
    fun `import result shows counts`() {
        setScreen(BackupUiState.Imported(alarmCount = 3, audioFileCount = 1))

        compose.onNodeWithText("Restored 3 alarms and 1 sounds.").assertIsDisplayed()
    }

    @Test
    fun `errors are shown with their reason`() {
        setScreen(BackupUiState.Failed(BackupError.NEWER_VERSION))

        compose.onNodeWithTag("backup_error").assertIsDisplayed()
        compose
            .onNodeWithText("This backup was made by a newer version of Alarme.")
            .assertIsDisplayed()
    }

    @Test
    fun `the replace warning is always visible`() {
        setScreen(BackupUiState.Idle)

        compose.onNodeWithText("Restoring replaces the current alarm list.").assertIsDisplayed()
    }
}
