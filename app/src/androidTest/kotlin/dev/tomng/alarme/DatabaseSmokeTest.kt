package dev.tomng.alarme

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.data.db.AlarmeDatabase
import dev.tomng.alarme.core.data.entity.AlarmInfoEntity
import dev.tomng.alarme.core.data.entity.MissionInfoEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room's generated code against the device's real SQLite, including the foreign-key cascade. */
@RunWith(AndroidJUnit4::class)
class DatabaseSmokeTest {

    private lateinit var db: AlarmeDatabase

    @Before
    fun open() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AlarmeDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
    }

    @After fun close() = db.close()

    @Test
    fun anAlarmWithMissionsRoundTrips() {
        runBlocking {
            val id = db.alarmDao().insert(alarm("Gym"))
            db.missionDao()
                .insertAll(
                    listOf(MissionInfoEntity(alarmId = id, missionType = "MATH", sortOrder = 0)),
                )

            assertThat(db.alarmDao().getById(id)?.name).isEqualTo("Gym")
            assertThat(db.missionDao().missionsForAlarm(id).map { it.missionType })
                .containsExactly("MATH")
        }
    }

    @Test
    fun deletingAnAlarmDropsItsMissions() = runBlocking {
        val id = db.alarmDao().insert(alarm("Work"))
        db.missionDao()
            .insertAll(
                listOf(MissionInfoEntity(alarmId = id, missionType = "SHAKE", sortOrder = 0)),
            )

        db.alarmDao().deleteById(id)

        assertThat(db.missionDao().missionsForAlarm(id)).isEmpty()
    }

    private fun alarm(name: String) =
        AlarmInfoEntity(
            type = "NORMAL",
            emoji = "⏰",
            name = name,
            hour = 7,
            minutes = 30,
            time = 0L,
            enabled = true,
            daysOfWeek = 62,
            alert = null,
            volume = 0.8,
            vibrate = false,
            label = "",
            isSnoozeEnable = true,
            snoozeDuration = 5.0,
            snoozeLimitCount = 3,
            wakeUpCheck = 0,
            isCrescendoEnable = false,
            crescendoDuration = 15,
            backupSound = false,
            timePressure = false,
            labelReminder = false,
            isSkipped = false,
            createdAt = 0L,
            updatedAt = 0L,
        )
}
