package dev.tomng.alarme.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tomng.alarme.core.data.db.AlarmeDatabase
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingEventRepositoryTest {

    private lateinit var db: AlarmeDatabase
    private lateinit var repository: RingEventRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, AlarmeDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = RingEventRepositoryImpl(db.alarmEventDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun event(type: AlarmEventType, timestamp: String, rootId: String = "root-1") =
        AlarmEvent(
            id = 0L,
            event = type,
            ringRootId = rootId,
            timestamp = Instant.parse(timestamp),
            triggerType = "NORMAL",
            missionsJson = null,
            alarmName = "morning",
            alarmEmoji = "⏰",
            scheduleId = "device_7_0630_2310",
        )

    @Test
    fun logAssignsIdAndRoundTrips() = runTest {
        val id = repository.logEvent(event(AlarmEventType.RING, "2026-11-04T05:30:00Z"))
        val loaded = repository.observeRecent(limit = 10).first().single()
        assertEquals(id, loaded.id)
        assertEquals(AlarmEventType.RING, loaded.event)
        assertEquals("device_7_0630_2310", loaded.scheduleId)
    }

    @Test
    fun observeRecentIsNewestFirst() = runTest {
        repository.logEvent(event(AlarmEventType.RING, "2026-11-04T05:30:00Z"))
        repository.logEvent(event(AlarmEventType.DISMISS, "2026-11-04T05:41:00Z"))
        repository.logEvent(event(AlarmEventType.SNOOZE, "2026-11-05T05:30:00Z", rootId = "root-2"))
        val events = repository.observeRecent(limit = 10).first()
        assertEquals(
            listOf(AlarmEventType.SNOOZE, AlarmEventType.DISMISS, AlarmEventType.RING),
            events.map { it.event },
        )
    }

    @Test
    fun limitBoundsHistory() = runTest {
        (1..5).forEach {
            repository.logEvent(event(AlarmEventType.RING, "2026-11-0${it}T05:30:00Z"))
        }
        assertEquals(3, repository.observeRecent(limit = 3).first().size)
    }

    @Test
    fun purgeDropsOnlyOldEvents() = runTest {
        repository.logEvent(event(AlarmEventType.RING, "2026-10-01T05:30:00Z"))
        repository.logEvent(event(AlarmEventType.RING, "2026-11-04T05:30:00Z"))
        repository.purgeOlderThan(Instant.parse("2026-11-01T00:00:00Z"))
        val remaining = repository.observeRecent(limit = 10).first()
        assertEquals(1, remaining.size)
        assertTrue(remaining.single().timestamp > Instant.parse("2026-11-01T00:00:00Z"))
    }
}
