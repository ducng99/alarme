package dev.tomng.alarme.feature.backup

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.backup.BackupAudioEntry
import dev.tomng.alarme.core.model.backup.BackupPayload
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import kotlin.time.Instant
import org.junit.Test

class BackupCodecTest {

    private val codec = BackupCodec()
    private val now = Instant.parse("2026-11-12T19:00:00Z")

    private fun payload(version: Int = BackupPayload.CURRENT_VERSION, alarms: Int = 1) =
        BackupPayload(
            formatVersion = version,
            exportedAt = now,
            alarms =
                (1..alarms).map {
                    Alarm(
                        id = it.toLong(),
                        name = "alarm $it",
                        hour = 6,
                        minute = 0,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            ringtones =
                listOf(
                    CatalogRingtone(1L, RingtoneKind.MUSIC, "file:///a.mp3", "A", now),
                ),
            audio = listOf(BackupAudioEntry("file:///a.mp3", "audio/a.mp3")),
        )

    @Test
    fun `encode then decode is lossless`() {
        val original = payload()

        val decoded = codec.decode(codec.encode(original))

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `garbage is rejected as invalid data`() {
        val error = runCatching { codec.decode("not json at all") }.exceptionOrNull()

        assertThat(error).isInstanceOf(BackupFormatException::class.java)
        assertThat((error as BackupFormatException).error).isEqualTo(BackupError.INVALID_DATA)
    }

    @Test
    fun `a newer format version is rejected`() {
        val text = codec.encode(payload(version = BackupPayload.CURRENT_VERSION + 1))

        val error = runCatching { codec.decode(text) }.exceptionOrNull()

        assertThat((error as BackupFormatException).error).isEqualTo(BackupError.NEWER_VERSION)
    }

    @Test
    fun `an older format version is accepted`() {
        val text = codec.encode(payload(version = 0))

        assertThat(codec.decode(text).alarms).hasSize(1)
    }

    @Test
    fun `too many alarms are rejected`() {
        val text = codec.encode(payload(alarms = BackupCodec.MAX_ALARMS + 1))

        val error = runCatching { codec.decode(text) }.exceptionOrNull()

        assertThat((error as BackupFormatException).error).isEqualTo(BackupError.TOO_MANY_ALARMS)
    }

    @Test
    fun `domain invariant violations are reported as invalid data`() {
        val text =
            """
            {"formatVersion":1,"exportedAt":"2026-11-12T19:00:00Z","alarms":[
              {"id":1,"name":"bad","hour":25,"minute":0,
               "createdAt":"2026-11-12T19:00:00Z","updatedAt":"2026-11-12T19:00:00Z"}],
             "ringtones":[],"audio":[]}
            """
                .trimIndent()

        val error = runCatching { codec.decode(text) }.exceptionOrNull()

        assertThat((error as BackupFormatException).error).isEqualTo(BackupError.INVALID_DATA)
    }
}
