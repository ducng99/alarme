package dev.tomng.alarme.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.data.db.AlarmeDatabase
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Skip once: only enabled, repeating alarms can be skipped — a one-shot alarm is turned off
 * instead, and a disabled alarm has nothing to skip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SkipAlarmUseCaseTest {

    private lateinit var db: AlarmeDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var useCase: SkipAlarmUseCase
    private val clock = FakeAppClock(Instant.parse("2026-11-10T06:00:00Z"), TimeZone.UTC)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, AlarmeDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            AlarmRepositoryImpl(db.alarmDao(), db.missionDao(), db.typingPhraseDao(), clock)
        useCase = SkipAlarmUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(
        enabled: Boolean = true,
        days: DaysOfWeek = DaysOfWeek.EVERY_DAY,
    ): Long =
        repository.upsertAlarm(
            Alarm(
                id = Alarm.NEW_ID,
                name = "work",
                hour = 7,
                minute = 0,
                enabled = enabled,
                daysOfWeek = days,
                createdAt = clock.now,
                updatedAt = clock.now,
            ),
        )

    @Test
    fun `an enabled repeating alarm can be skipped once`() = runTest {
        val id = seed()

        val result = useCase.skip(id)

        assertThat(result).isEqualTo(SkipResult.Skipped)
        assertThat(repository.getAlarm(id)!!.isSkipped).isTrue()
    }

    @Test
    fun `a disabled alarm cannot be skipped`() = runTest {
        val id = seed(enabled = false)

        val result = useCase.skip(id)

        assertThat(result).isEqualTo(SkipResult.Denied(SkipDenial.Disabled))
        assertThat(repository.getAlarm(id)!!.isSkipped).isFalse()
    }

    @Test
    fun `a one-shot alarm cannot be skipped`() = runTest {
        val id = seed(days = DaysOfWeek.NONE)

        val result = useCase.skip(id)

        assertThat(result).isEqualTo(SkipResult.Denied(SkipDenial.NotRepeating))
        assertThat(repository.getAlarm(id)!!.isSkipped).isFalse()
    }

    @Test
    fun `a missing alarm cannot be skipped`() = runTest {
        assertThat(useCase.skip(999L)).isEqualTo(SkipResult.Denied(SkipDenial.Missing))
    }

    @Test
    fun `undo clears the skip`() = runTest {
        val id = seed()
        useCase.skip(id)

        useCase.undoSkip(id)

        assertThat(repository.getAlarm(id)!!.isSkipped).isFalse()
    }
}
