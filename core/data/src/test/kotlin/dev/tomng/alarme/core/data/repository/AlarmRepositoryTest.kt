package dev.tomng.alarme.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.data.db.AlarmeDatabase
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmRepositoryTest {

    private lateinit var db: AlarmeDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var clock: FakeAppClock
    private val berlin = TimeZone.of("Europe/Berlin")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, AlarmeDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        clock = FakeAppClock(now = Instant.parse("2026-11-03T22:00:00Z"), zone = berlin)
        repository =
            AlarmRepositoryImpl(db.alarmDao(), db.missionDao(), db.typingPhraseDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun alarm(hour: Int, minute: Int = 0, missions: List<Mission> = emptyList()) =
        Alarm(
            id = Alarm.NEW_ID,
            name = "morning",
            hour = hour,
            minute = minute,
            daysOfWeek = DaysOfWeek.EVERY_DAY,
            missions = missions,
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )

    @Test
    fun insertAssignsIdAndComputesNextOccurrence() = runTest {
        val id = repository.upsertAlarm(alarm(hour = 6, minute = 30))
        val loaded = repository.getAlarm(id)!!
        assertTrue(id > 0)
        // now = Nov 3 23:00 Berlin -> next occurrence is Nov 4 06:30 Berlin (= 05:30 UTC)
        assertEquals(Instant.parse("2026-11-04T05:30:00Z"), loaded.nextOccurrence)
    }

    @Test
    fun updateKeepsId() = runTest {
        val id = repository.upsertAlarm(alarm(hour = 6))
        val updated = repository.getAlarm(id)!!.copy(name = "renamed", hour = 7)
        val id2 = repository.upsertAlarm(updated)
        assertEquals(id, id2)
        assertEquals("renamed", repository.getAlarm(id)!!.name)
    }

    @Test
    fun observeAlarmsEmitsDomainModels() = runTest {
        repository.upsertAlarm(alarm(hour = 8))
        repository.upsertAlarm(alarm(hour = 5))
        val alarms = repository.observeAlarms().first()
        assertEquals(2, alarms.size)
        assertEquals(5, alarms[0].hour) // sorted by denormalised next-occurrence time
    }

    @Test
    fun setSkippedChangesDomainFlag() = runTest {
        val id = repository.upsertAlarm(alarm(hour = 6))
        repository.setSkipped(id, skipped = true)
        assertTrue(repository.getAlarm(id)!!.isSkipped)
    }

    @Test
    fun deleteRemovesAlarm() = runTest {
        val id = repository.upsertAlarm(alarm(hour = 6))
        repository.deleteAlarm(id)
        assertNull(repository.getAlarm(id))
    }

    @Test
    fun missionsSurviveRoundTripThroughRepository() = runTest {
        val missions =
            listOf(
                Mission.Math(timeLimitSeconds = 60, rounds = 2, difficulty = 1),
                Mission.Shake(timeLimitSeconds = 30, count = 15),
            )
        val id = repository.upsertAlarm(hourAlarmWith(missions))
        val restored = repository.getAlarm(id)!!
        assertEquals(missions, restored.missions)
    }

    @Test
    fun observeNextAlarmKeepsSkippedAlarmsScheduled() = runTest {
        val a = repository.upsertAlarm(alarm(hour = 5))
        val b = repository.upsertAlarm(alarm(hour = 9))
        assertEquals(a, repository.observeNextAlarm().first()!!.id) // earliest first
        repository.setSkipped(a, skipped = true)
        assertEquals(b, repository.observeNextAlarm().first()!!.id)
        repository.setSkipped(b, skipped = true)
        // Both skips are pending: tomorrow 05:00 is still the next ring, so the header names a.
        assertEquals(a, repository.observeNextAlarm().first()!!.id)
        repository.setEnabled(a, enabled = false)
        repository.setEnabled(b, enabled = false)
        assertNull(repository.observeNextAlarm().first())
    }

    @Test
    fun observeNextAlarmRecomputesInsteadOfTrustingTheStoredTime() = runTest {
        // Stored at 22:00 Berlin: the 5:00 alarm's column says tomorrow 05:00.
        val early = repository.upsertAlarm(alarm(hour = 5))
        val late = repository.upsertAlarm(alarm(hour = 9))

        // Two days later the stored column is stale (in the past) for both; the next alarm must
        // still be the earliest *upcoming* one, not the one with the oldest stale timestamp.
        clock.now = Instant.parse("2026-11-05T23:00:00Z")

        assertEquals(early, repository.observeNextAlarm().first()!!.id)
        assertEquals(
            Instant.parse("2026-11-06T04:00:00Z"),
            repository.observeNextAlarm().first()!!.nextOccurrence,
        )
        assertEquals(late, repository.observeAlarms().first().last().id)
    }

    private fun hourAlarmWith(missions: List<Mission>): Alarm = alarm(hour = 7, missions = missions)
}
