package dev.tomng.alarme.feature.backup

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val uri = Uri.parse("content://test/backup.zip")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeOperations : BackupOperations {
        var exportResult: ExportSummary = ExportSummary(2, 1)
        var importResult: ImportSummary = ImportSummary(3, 2, 2)
        var failure: Exception? = null
        var exports = 0
        var imports = 0

        override suspend fun export(uri: Uri): ExportSummary {
            exports++
            failure?.let { throw it }
            return exportResult
        }

        override suspend fun import(uri: Uri): ImportSummary {
            imports++
            failure?.let { throw it }
            return importResult
        }
    }

    @Test
    fun `export reports counts`() = runTest {
        val operations = FakeOperations()
        val viewModel = BackupViewModel(operations)

        viewModel.export(uri)
        assertThat(viewModel.state.value).isEqualTo(BackupUiState.Working)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(BackupUiState.Exported(2, 1))
    }

    @Test
    fun `import reports counts`() = runTest {
        val viewModel = BackupViewModel(FakeOperations())

        viewModel.import(uri)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(BackupUiState.Imported(3, 2))
    }

    @Test
    fun `format errors surface their reason`() = runTest {
        val operations =
            FakeOperations().apply { failure = BackupFormatException(BackupError.NEWER_VERSION) }
        val viewModel = BackupViewModel(operations)

        viewModel.import(uri)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(BackupUiState.Failed(BackupError.NEWER_VERSION))
    }

    @Test
    fun `unexpected errors surface as cannot-open`() = runTest {
        val operations = FakeOperations().apply { failure = IllegalStateException("boom") }
        val viewModel = BackupViewModel(operations)

        viewModel.export(uri)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(BackupUiState.Failed(BackupError.CANNOT_OPEN))
    }

    @Test
    fun `a second request while working is ignored`() = runTest {
        val operations = FakeOperations()
        val viewModel = BackupViewModel(operations)

        viewModel.export(uri)
        viewModel.export(uri)
        advanceUntilIdle()

        assertThat(operations.exports).isEqualTo(1)
    }

    @Test
    fun `clear returns to idle`() = runTest {
        val viewModel = BackupViewModel(FakeOperations())
        viewModel.export(uri)
        advanceUntilIdle()

        viewModel.clear()

        assertThat(viewModel.state.value).isEqualTo(BackupUiState.Idle)
    }
}
