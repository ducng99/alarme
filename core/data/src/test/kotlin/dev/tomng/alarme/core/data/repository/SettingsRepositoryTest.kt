package dev.tomng.alarme.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.tomng.alarme.core.data.settings.AudioOutputPreference
import dev.tomng.alarme.core.model.alarm.AutoDismissOption
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var file: File

    @Before
    fun setUp() {
        file = File(context.filesDir, "test_${System.nanoTime()}.preferences_pb")
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    private fun newRepository(): SettingsRepository =
        SettingsRepositoryImpl(
            scope = scope,
            file = file,
        )

    @Test
    fun defaultsMatchSpec() = runTest {
        val settings = newRepository().settings.first()
        assertEquals(true, settings.muteEnabled)
        assertEquals(3, settings.muteLimit)
        assertEquals(true, settings.muteDuringMission)
        assertEquals(3, settings.missionMuteMinutes)
        assertEquals(AutoDismissOption.Off, settings.autoDismiss)
        assertEquals(AudioOutputPreference.ALARM_STREAM, settings.audioOutput)
        assertEquals(false, settings.preventPowerOff)
        assertEquals(false, settings.preventUninstall)
        assertEquals(null, settings.defaultRingtoneId)
        assertEquals(false, settings.onboardingDone)
    }

    @Test
    fun onboardingDonePersists() = runTest {
        newRepository().setOnboardingDone(true)

        assertEquals(true, newRepository().settings.first().onboardingDone)
    }

    @Test
    fun writesRoundTrip() = runTest {
        val repo = newRepository()
        repo.setMute(enabled = false, limit = 1)
        repo.setMuteDuringMission(false)
        repo.setMissionMuteMinutes(7)
        repo.setAutoDismiss(AutoDismissOption.After(10))
        repo.setAudioOutput(AudioOutputPreference.EXTERNAL)
        repo.setPreventPowerOff(true)
        repo.setPreventUninstall(true)
        repo.setDefaultRingtoneId(42L)

        val settings = repo.settings.first()
        assertEquals(false, settings.muteEnabled)
        assertEquals(1, settings.muteLimit)
        assertEquals(false, settings.muteDuringMission)
        assertEquals(7, settings.missionMuteMinutes)
        assertEquals(AutoDismissOption.After(10), settings.autoDismiss)
        assertEquals(AudioOutputPreference.EXTERNAL, settings.audioOutput)
        assertEquals(true, settings.preventPowerOff)
        assertEquals(true, settings.preventUninstall)
        assertEquals(42L, settings.defaultRingtoneId)
    }

    @Test
    fun turningAutoDismissBackOff() = runTest {
        val repo = newRepository()
        repo.setAutoDismiss(AutoDismissOption.After(10))
        repo.setAutoDismiss(AutoDismissOption.Off)
        assertEquals(AutoDismissOption.Off, repo.settings.first().autoDismiss)
    }

    @Test
    fun deviceIdIsStableAcrossInstances() = runTest {
        val first = newRepository().deviceId()
        val second = newRepository().deviceId()
        assertEquals(first, second)
    }

    @Test
    fun deviceIdIsUniquePerInstallation() = runTest {
        val a = newRepository().deviceId()
        val otherFile = File(context.filesDir, "test_other_${System.nanoTime()}.preferences_pb")
        val otherRepo =
            SettingsRepositoryImpl(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                file = otherFile,
            )
        val b = otherRepo.deviceId()
        assertNotEquals(a, b)
        otherFile.delete()
    }
}
