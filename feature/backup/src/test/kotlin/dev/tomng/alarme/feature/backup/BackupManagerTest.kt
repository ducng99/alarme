package dev.tomng.alarme.feature.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery
import dev.tomng.alarme.core.alarmengine.scheduling.SchedulingResult
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.data.repository.BackupRepository
import dev.tomng.alarme.core.data.ringtone.RingtonePaths
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.backup.BackupSnapshot
import dev.tomng.alarme.core.model.backup.RestoreResult
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var repository: FakeBackupRepository
    private lateinit var scheduler: RecordingScheduler
    private lateinit var paths: RingtonePaths
    private lateinit var manager: BackupManager
    private lateinit var clock: FakeAppClock
    private val now = Instant.parse("2026-11-12T19:00:00Z")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = FakeBackupRepository()
        scheduler = RecordingScheduler()
        paths = RingtonePaths(context)
        clock = FakeAppClock(now = now)
        manager =
            BackupManager(
                contentResolver = context.contentResolver,
                repository = repository,
                codec = BackupCodec(),
                paths = paths,
                clock = clock,
                scheduler = scheduler,
            )
    }

    private fun alarm(name: String, ringtoneUri: String? = null) =
        Alarm(
            id = Alarm.NEW_ID,
            name = name,
            hour = 6,
            minute = 30,
            ringtoneUri = ringtoneUri,
            createdAt = now,
            updatedAt = now,
        )

    private fun fileUri(name: String): Pair<File, String> {
        val file =
            File(context.filesDir, "ringtone/music/$name").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(9, 8, 7))
            }
        return file to Uri.fromFile(file).toString()
    }

    private fun exportFile(): Uri =
        Uri.fromFile(File(context.cacheDir, "backup.zip").apply { parentFile?.mkdirs() })

    @Test
    fun `export writes alarms and copies local sounds`() = runTest {
        val (_, uri) = fileUri("song.mp3")
        repository.snapshotResult =
            BackupSnapshot(
                alarms = listOf(alarm("a", ringtoneUri = uri)),
                ringtones = listOf(CatalogRingtone(1L, RingtoneKind.MUSIC, uri, "Song", now)),
            )
        val target = exportFile()

        val summary = manager.export(target)

        assertThat(summary.alarmCount).isEqualTo(1)
        assertThat(summary.audioFileCount).isEqualTo(1)
        val contents = BackupArchive.read(File(target.path!!).inputStream())
        assertThat(contents.audio.keys).containsExactly("audio/song.mp3")
        assertThat(contents.json).contains("\"Song\"")
    }

    @Test
    fun `export skips sounds whose file is gone`() = runTest {
        repository.snapshotResult =
            BackupSnapshot(
                alarms = listOf(alarm("a", ringtoneUri = "file:///nope/missing.mp3")),
                ringtones =
                    listOf(
                        CatalogRingtone(
                            1L,
                            RingtoneKind.MUSIC,
                            "file:///nope/missing.mp3",
                            "M",
                            now,
                        ),
                    ),
            )
        val target = exportFile()

        val summary = manager.export(target)

        assertThat(summary.audioFileCount).isEqualTo(0)
        assertThat(BackupArchive.read(File(target.path!!).inputStream()).audio).isEmpty()
    }

    @Test
    fun `export ignores device and bundled sounds`() = runTest {
        repository.snapshotResult =
            BackupSnapshot(
                ringtones =
                    listOf(
                        CatalogRingtone(
                            1L,
                            RingtoneKind.BUNDLED,
                            "android.resource://x/1",
                            "B",
                            now,
                        ),
                        CatalogRingtone(2L, RingtoneKind.DEVICE_ALARM, "content://x/2", "D", now),
                    ),
            )

        val summary = manager.export(exportFile())

        assertThat(summary.audioFileCount).isEqualTo(0)
    }

    @Test
    fun `import restores sounds under fresh names and rewrites references`() = runTest {
        val (_, oldUri) = fileUri("song.mp3")
        repository.snapshotResult =
            BackupSnapshot(
                alarms = listOf(alarm("a", ringtoneUri = oldUri)),
                ringtones = listOf(CatalogRingtone(1L, RingtoneKind.MUSIC, oldUri, "Song", now)),
            )
        val target = exportFile()
        manager.export(target)

        // Simulate a different device: nothing in the catalogue, but a file of the same name
        // already exists, so the restored copy must not overwrite it.
        repository.snapshotResult = BackupSnapshot()
        File(context.filesDir, "ringtone/music/song.mp3").writeBytes(byteArrayOf(1, 1, 1))

        val summary = manager.import(target)

        assertThat(summary.alarmCount).isEqualTo(1)
        assertThat(summary.audioFileCount).isEqualTo(1)
        val restored = repository.restoredAlarms.single()
        val newUri = restored.ringtoneUri!!
        assertThat(newUri).isNotEqualTo(oldUri)
        assertThat(File(Uri.parse(newUri).path!!).readBytes()).isEqualTo(byteArrayOf(9, 8, 7))
        assertThat(repository.restoredRingtones.single().uri).isEqualTo(newUri)
        assertThat(scheduler.rearmReasons).containsExactly(RescheduleReason.ADD_ALARM)
    }

    @Test
    fun `import keeps pseudo and device uris untouched`() = runTest {
        repository.snapshotResult =
            BackupSnapshot(
                alarms =
                    listOf(
                        alarm("silent", ringtoneUri = "uri_silent_ringtone"),
                        alarm("device", ringtoneUri = "content://media/1"),
                    ),
            )
        val target = exportFile()
        manager.export(target)
        repository.snapshotResult = BackupSnapshot()

        manager.import(target)

        assertThat(repository.restoredAlarms.map { it.ringtoneUri })
            .containsExactly("uri_silent_ringtone", "content://media/1")
            .inOrder()
    }

    @Test
    fun `import of a non-archive fails with a readable error`() = runTest {
        val target = Uri.fromFile(File(context.cacheDir, "junk.zip").apply { writeText("nope") })

        val error = runCatching { manager.import(target) }.exceptionOrNull()

        assertThat((error as BackupFormatException).error).isEqualTo(BackupError.NOT_AN_ARCHIVE)
        assertThat(repository.restoredAlarms).isEmpty()
    }

    @Test
    fun `import with a missing sound file fails before touching the database`() = runTest {
        repository.snapshotResult =
            BackupSnapshot(
                alarms = listOf(alarm("a", ringtoneUri = fileUri("song.mp3").second)),
                ringtones =
                    listOf(
                        CatalogRingtone(
                            1L,
                            RingtoneKind.MUSIC,
                            fileUri("song.mp3").second,
                            "Song",
                            now,
                        ),
                    ),
            )
        val target = exportFile()
        manager.export(target)
        // Rebuild the archive without its audio entry.
        val contents = BackupArchive.read(File(target.path!!).inputStream())
        BackupArchive.write(File(target.path!!).outputStream(), contents.json, emptyMap())
        repository.snapshotResult = BackupSnapshot()

        val error = runCatching { manager.import(target) }.exceptionOrNull()

        assertThat((error as BackupFormatException).error).isEqualTo(BackupError.MISSING_AUDIO)
        assertThat(repository.restoredAlarms).isEmpty()
    }

    @Test
    fun `import of an unreadable uri fails cleanly`() = runTest {
        val missing = Uri.fromFile(File(context.cacheDir, "missing.zip"))
        val error = runCatching { manager.import(missing) }.exceptionOrNull()

        assertThat((error as BackupFormatException).error).isEqualTo(BackupError.CANNOT_OPEN)
    }

    private class FakeBackupRepository : BackupRepository {
        var snapshotResult = BackupSnapshot()
        var restoredAlarms: List<Alarm> = emptyList()
        var restoredRingtones: List<CatalogRingtone> = emptyList()

        override suspend fun snapshot(): BackupSnapshot = snapshotResult

        override suspend fun restore(
            alarms: List<Alarm>,
            ringtones: List<CatalogRingtone>,
        ): RestoreResult {
            restoredAlarms = alarms
            restoredRingtones = ringtones
            return RestoreResult(
                alarms = alarms.mapIndexed { i, a -> a.copy(id = i + 1L) },
                ringtoneRowsAdded = ringtones.size,
            )
        }
    }

    private class RecordingScheduler : AlarmScheduler {
        val rearmReasons = mutableListOf<RescheduleReason>()

        override suspend fun rearm(reason: RescheduleReason): SchedulingResult {
            rearmReasons += reason
            return SchedulingResult(next = null, nextAt = null, missed = emptyList())
        }

        override fun armWakeUpCheck(alarmId: Long, at: Instant) = Unit

        override fun cancelWakeUpCheck() = Unit

        override fun armSnooze(
            alarmId: Long,
            scheduleId: String?,
            at: Instant,
            count: Int,
        ) = Unit

        override fun cancelSnooze() = Unit

        override fun armRetryRing(delivery: RingDelivery, at: Instant) = Unit
    }
}
