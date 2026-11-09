package dev.tomng.alarme.core.alarmengine.poweroff

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PowerOffBlockerControllerTest {

    private val settings = FakeSettingsRepository()
    private val channel = FakeBlockerChannel()
    private val controller = PowerOffBlockerController(settings, channel)

    @Test
    fun `a disabled toggle leaves the channel alone`() = runTest {
        controller.onFocusLost(dismissed = false, screenKeeperPaused = false)

        assertThat(channel.blocks).isEqualTo(0)
    }

    @Test
    fun `an enabled toggle with a connected service blocks and counts`() = runTest {
        settings.settings.value = settings.settings.value.copy(preventPowerOff = true)

        controller.onFocusLost(dismissed = false, screenKeeperPaused = false)

        assertThat(channel.blocks).isEqualTo(1)
    }

    @Test
    fun `a missing accessibility grant degrades to nothing`() = runTest {
        settings.settings.value = settings.settings.value.copy(preventPowerOff = true)
        channel.connected = false

        controller.onFocusLost(dismissed = false, screenKeeperPaused = false)

        assertThat(channel.blocks).isEqualTo(0)
    }

    @Test
    fun `a dismissed alarm is not fought`() = runTest {
        settings.settings.value = settings.settings.value.copy(preventPowerOff = true)

        controller.onFocusLost(dismissed = true, screenKeeperPaused = false)

        assertThat(channel.blocks).isEqualTo(0)
    }
}

class FakeBlockerChannel : BlockerChannel {
    var connected = true
    var blocks = 0

    override fun isConnected(): Boolean = connected

    override fun block() {
        blocks++
    }
}
