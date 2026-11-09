package dev.tomng.alarme.feature.settings.permissions

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionCenterViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val probe = FakePermissionProbe()
    private val viewModel = PermissionCenterViewModel(probe)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `every capability is listed with its live state`() =
        runTest(dispatcher) {
            probe.granted += PermissionItem.NOTIFICATIONS
            viewModel.refresh()
            runCurrent()

            val rows = viewModel.state.value.rows
            assertThat(rows.map { it.item }).containsExactlyElementsIn(PermissionItem.entries)
            assertThat(rows.single { it.item == PermissionItem.NOTIFICATIONS }.granted).isTrue()
            assertThat(rows.single { it.item == PermissionItem.EXACT_ALARM }.granted).isFalse()
        }

    @Test
    fun `all granted is only true when nothing is missing`() =
        runTest(dispatcher) {
            probe.granted += PermissionItem.entries
            viewModel.refresh()
            runCurrent()
            assertThat(viewModel.state.value.allGranted).isTrue()

            probe.granted -= PermissionItem.OVERLAY
            viewModel.refresh()
            runCurrent()
            assertThat(viewModel.state.value.allGranted).isFalse()
        }

    @Test
    fun `refresh picks up a grant made in system settings`() =
        runTest(dispatcher) {
            viewModel.refresh()
            runCurrent()
            assertThat(viewModel.state.value.rows.single { it.item == PermissionItem.DND }.granted)
                .isFalse()

            probe.granted += PermissionItem.DND
            viewModel.refresh()
            runCurrent()

            assertThat(viewModel.state.value.rows.single { it.item == PermissionItem.DND }.granted)
                .isTrue()
        }

    @Test
    fun `the missing count drives the summary`() =
        runTest(dispatcher) {
            probe.granted += listOf(PermissionItem.NOTIFICATIONS, PermissionItem.EXACT_ALARM)
            viewModel.refresh()
            runCurrent()

            assertThat(viewModel.state.value.missingCount)
                .isEqualTo(PermissionItem.entries.size - 2)
        }
}

class FakePermissionProbe : PermissionProbe {
    val granted = mutableSetOf<PermissionItem>()

    override fun isGranted(item: PermissionItem): Boolean = item in granted
}
