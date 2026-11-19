package dev.tomng.alarme.feature.settings.sound

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.data.repository.SettingsRepository
import dev.tomng.alarme.core.data.settings.AppSettings
import dev.tomng.alarme.core.data.settings.AudioOutputPreference
import dev.tomng.alarme.core.model.alarm.AutoDismissOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SoundSettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = FakeSettings()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `toggling mute during mission persists and is reflected`() =
        runTest(dispatcher) {
            val viewModel = SoundSettingsViewModel(settings)

            viewModel.setMuteDuringMission(false)

            assertThat(settings.state.value.muteDuringMission).isFalse()
            assertThat(viewModel.state.value.muteDuringMission).isFalse()
        }
}

private class FakeSettings : SettingsRepository {
    val state = MutableStateFlow(AppSettings())

    override val settings = state

    override suspend fun setMute(enabled: Boolean, limit: Int) = Unit

    override suspend fun setMuteDuringMission(enabled: Boolean) {
        state.value = state.value.copy(muteDuringMission = enabled)
    }

    override suspend fun setMissionMuteMinutes(minutes: Int) {
        state.value = state.value.copy(missionMuteMinutes = minutes)
    }

    override suspend fun setAutoDismiss(option: AutoDismissOption) = Unit

    override suspend fun setAudioOutput(output: AudioOutputPreference) = Unit

    override suspend fun setPreventPowerOff(enabled: Boolean) = Unit

    override suspend fun setPreventUninstall(enabled: Boolean) = Unit

    override suspend fun setDefaultRingtoneId(id: Long?) = Unit

    override suspend fun setOnboardingDone(done: Boolean) = Unit

    override suspend fun deviceId(): String = "dev"
}
