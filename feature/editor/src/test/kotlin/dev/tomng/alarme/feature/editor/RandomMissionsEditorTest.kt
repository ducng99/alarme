package dev.tomng.alarme.feature.editor

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Instant
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

@OptIn(ExperimentalCoroutinesApi::class)
class RandomMissionsEditorTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeAppClock(now = Instant.parse("2026-05-05T05:05:00Z"))
    private val repository = FakeAlarmRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        AlarmEditorViewModel(repository, RecordingScheduler(), clock, FakeRingtoneRepository())

    @Test
    fun `turning random missions on defaults to every mission`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()
        repeat(4) { vm.addMission(Mission.Math()) }

        vm.setRandomMissions(true)

        assertThat(vm.state.value.draft.randomMissionCount).isEqualTo(4)
    }

    @Test
    fun `random missions need at least two missions`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()
        vm.addMission(Mission.Math())

        vm.setRandomMissions(true)

        assertThat(vm.state.value.draft.randomMissionCount).isNull()
    }

    @Test
    fun `the random count stays within the chain`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()
        repeat(3) { vm.addMission(Mission.Math()) }
        vm.setRandomMissions(true)

        vm.setRandomMissionCount(9)
        assertThat(vm.state.value.draft.randomMissionCount).isEqualTo(3)

        vm.setRandomMissionCount(0)
        assertThat(vm.state.value.draft.randomMissionCount).isEqualTo(1)
    }

    @Test
    fun `removing missions shrinks the random count and turns it off below two`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()
        repeat(3) { vm.addMission(Mission.Math()) }
        vm.setRandomMissions(true)

        vm.removeMission(0)
        assertThat(vm.state.value.draft.randomMissionCount).isEqualTo(2)

        vm.removeMission(0)
        assertThat(vm.state.value.draft.randomMissionCount).isNull()
    }

    @Test
    fun `turning random missions off keeps the whole chain`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()
        repeat(3) { vm.addMission(Mission.Math()) }
        vm.setRandomMissions(true)

        vm.setRandomMissions(false)

        assertThat(vm.state.value.draft.randomMissionCount).isNull()
        assertThat(vm.state.value.draft.missions).hasSize(3)
    }

    @Test
    fun `the saved alarm carries the random count`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()
        repeat(5) { vm.addMission(Mission.Shake()) }
        vm.setRandomMissions(true)
        vm.setRandomMissionCount(3)

        vm.save()
        advanceUntilIdle()

        assertThat(repository.upserts.single().randomMissionCount).isEqualTo(3)
    }
}
