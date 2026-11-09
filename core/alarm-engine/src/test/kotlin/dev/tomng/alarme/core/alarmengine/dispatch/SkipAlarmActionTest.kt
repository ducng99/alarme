package dev.tomng.alarme.core.alarmengine.dispatch

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.FakeAlarmRepository
import dev.tomng.alarme.core.data.repository.SkipAlarmUseCase
import dev.tomng.alarme.core.model.alarm.Alarm
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The reminder action's work: the skip use case, launched off the receiver thread. */
class SkipAlarmActionTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the action skips the alarm through the use case`() = runTest {
        val repo = FakeAlarmRepository()
        repo.alarms.value =
            listOf(
                Alarm(
                    id = 7,
                    name = "work",
                    hour = 7,
                    minute = 0,
                    createdAt = Instant.parse("2026-11-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-11-01T00:00:00Z"),
                ),
            )
        val action =
            UseCaseSkipAlarmAction(
                useCase = SkipAlarmUseCase(repo),
                scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
            )

        action.skip(7L)

        assertThat(repo.alarms.value.single().isSkipped).isTrue()
    }
}
