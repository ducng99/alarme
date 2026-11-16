package dev.tomng.alarme.feature.settings.reliability

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.poweroff.BlockCounterStore
import dev.tomng.alarme.core.alarmengine.poweroff.BlockerChannel
import dev.tomng.alarme.core.alarmengine.uninstall.UninstallGuard
import dev.tomng.alarme.core.data.repository.SettingsRepository
import dev.tomng.alarme.core.data.settings.AppSettings
import dev.tomng.alarme.core.model.alarm.AutoDismissOption
import dev.tomng.alarme.feature.settings.permissions.FakePermissionProbe
import dev.tomng.alarme.feature.settings.permissions.PermissionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReliabilityViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettings()
    private val channel = FakeChannel()
    private val counter = FakeCounter()
    private val guard = FakeGuard()
    private val probe = FakePermissionProbe()
    private val viewModel = ReliabilityViewModel(settings, channel, counter, guard, probe)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the toggle reflects the stored preference`() =
        runTest(dispatcher) {
            settings.state.value = settings.state.value.copy(preventPowerOff = true)
            viewModel.refresh()
            runCurrent()

            assertThat(viewModel.state.value.preventPowerOff).isTrue()
        }

    @Test
    fun `the blocker is only armed when both grants are present`() =
        runTest(dispatcher) {
            settings.state.value = settings.state.value.copy(preventPowerOff = true)
            probe.granted += PermissionItem.OVERLAY
            viewModel.refresh()
            runCurrent()

            assertThat(viewModel.state.value.preventPowerOffArmed).isFalse()

            channel.connected = true
            viewModel.refresh()
            runCurrent()

            assertThat(viewModel.state.value.preventPowerOffArmed).isTrue()
        }

    @Test
    fun `turning prevent power off on persists the preference`() =
        runTest(dispatcher) {
            viewModel.setPreventPowerOff(true)
            runCurrent()

            assertThat(settings.state.value.preventPowerOff).isTrue()
        }

    @Test
    fun `the uninstall toggle follows the live admin state`() =
        runTest(dispatcher) {
            guard.active = true
            viewModel.refresh()
            runCurrent()

            assertThat(viewModel.state.value.preventUninstall).isTrue()
        }

    @Test
    fun `turning prevent uninstall off removes the admin`() =
        runTest(dispatcher) {
            guard.active = true
            viewModel.refresh()
            runCurrent()

            viewModel.setPreventUninstall(false)
            runCurrent()

            assertThat(guard.active).isFalse()
            assertThat(settings.state.value.preventUninstall).isFalse()
            assertThat(viewModel.state.value.preventUninstall).isFalse()
        }

    @Test
    fun `the block count is surfaced`() =
        runTest(dispatcher) {
            counter.state.value = 7
            viewModel.refresh()
            runCurrent()

            assertThat(viewModel.state.value.blockedCount).isEqualTo(7)
        }
}

private class FakeSettings : SettingsRepository {
    val state = MutableStateFlow(AppSettings())

    override val settings = state

    override suspend fun setMute(enabled: Boolean, limit: Int) = Unit

    override suspend fun setAutoDismiss(option: AutoDismissOption) = Unit

    override suspend fun setAudioOutput(
        output: dev.tomng.alarme.core.data.settings.AudioOutputPreference,
    ) = Unit

    override suspend fun setPreventPowerOff(enabled: Boolean) {
        state.value = state.value.copy(preventPowerOff = enabled)
    }

    override suspend fun setPreventUninstall(enabled: Boolean) {
        state.value = state.value.copy(preventUninstall = enabled)
    }

    override suspend fun setDefaultRingtoneId(id: Long?) = Unit

    override suspend fun setOnboardingDone(done: Boolean) = Unit

    override suspend fun deviceId(): String = "dev"
}

private class FakeChannel : BlockerChannel {
    var connected = false

    override fun isConnected(): Boolean = connected

    override fun block() = Unit
}

private class FakeCounter : BlockCounterStore {
    val state = MutableStateFlow(0)

    override val count = state

    override suspend fun increment() {
        state.value += 1
    }
}

private class FakeGuard : UninstallGuard {
    var active = false

    override fun isActive(): Boolean = active

    override fun requestIntent(): android.content.Intent = android.content.Intent()

    override fun disable() {
        active = false
    }
}
