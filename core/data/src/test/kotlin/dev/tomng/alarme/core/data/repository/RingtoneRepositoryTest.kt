package dev.tomng.alarme.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tomng.alarme.core.data.db.AlarmeDatabase
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
class RingtoneRepositoryTest {

    private lateinit var db: AlarmeDatabase
    private lateinit var repository: RingtoneRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, AlarmeDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = RingtoneRepositoryImpl(db.ringtoneDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertReturnsIdAndObservesMappedRowsOldestFirst() = runTest {
        val first = repository.insert(RingtoneKind.MUSIC, "file://a.mp3", "A")
        val second = repository.insert(RingtoneKind.RECORD, "file://b.m4a", "B")

        val rows = repository.observeAll().first()

        assertTrue(first > 0 && second > first)
        assertEquals(2, rows.size)
        assertEquals(first, rows[0].id)
        assertEquals(RingtoneKind.MUSIC, rows[0].kind)
        assertEquals("file://a.mp3", rows[0].uri)
        assertEquals(RingtoneKind.RECORD, rows[1].kind)
    }

    @Test
    fun getByIdMapsKind() = runTest {
        val id = repository.insert(RingtoneKind.BUNDLED, "android.resource://x", "Bundled")

        assertEquals(RingtoneKind.BUNDLED, repository.getById(id)?.kind)
        assertNull(repository.getById(id + 999))
    }

    @Test
    fun deleteRemovesTheRow() = runTest {
        val id = repository.insert(RingtoneKind.MUSIC, "file://a.mp3", "A")

        repository.delete(id)

        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun bundledRowsAreSeededOnceAndIdempotently() = runTest {
        val bundle =
            listOf(
                "Gentle chime" to "android.resource://pkg/raw/gentle",
                "Radar" to "android.resource://pkg/raw/radar",
            )

        repository.seedBundled(bundle)
        repository.seedBundled(bundle)

        val rows = repository.observeAll().first()
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.kind == RingtoneKind.BUNDLED })
        assertEquals("Gentle chime", rows[0].title)
    }
}
