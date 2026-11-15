package dev.tomng.alarme.core.alarmengine.ring

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.data.settings.AppSettings
import dev.tomng.alarme.core.data.settings.AudioOutputPreference
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.CrescendoOption
import dev.tomng.alarme.core.model.alarm.MemoOption
import kotlin.time.Instant
import org.junit.Test

/** Table-driven tests of the ring-spec computation (`AlarmService.f()` shape). */
class RingRequestBuilderTest {

    private fun alarm(
        volumeRatio: Float = 1f,
        vibrate: Boolean = false,
        crescendo: CrescendoOption = CrescendoOption(),
        backupEnabled: Boolean = false,
        timeTts: Boolean = false,
        memoTts: Boolean = false,
        memo: String = "take the bins out",
        ringtoneUri: String? = null,
    ) =
        Alarm(
            id = 1,
            name = "work",
            hour = 7,
            minute = 0,
            volumeRatio = volumeRatio,
            vibrate = vibrate,
            crescendoOption = crescendo,
            backupSoundEnabled = backupEnabled,
            timeAnnouncementEnabled = timeTts,
            memoOption = MemoOption(memo = memo, ttsEnabled = memoTts),
            ringtoneUri = ringtoneUri,
            createdAt = Instant.parse("2026-11-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-11-01T00:00:00Z"),
        )

    private fun build(
        alarm: Alarm = alarm(),
        settings: AppSettings = AppSettings(),
        external: Boolean = false,
    ) = RingRequestBuilder.build(alarm, settings, hasExternalOutput = external)

    @Test
    fun `alarm stream when no external output`() {
        val request =
            build(
                settings = AppSettings(audioOutput = AudioOutputPreference.EXTERNAL),
                external = false,
            )
        assertThat(request.useAlarmStream).isTrue()
    }

    @Test
    fun `alarm stream when external output but user prefers alarm speaker`() {
        val request =
            build(
                settings = AppSettings(audioOutput = AudioOutputPreference.ALARM_STREAM),
                external = true,
            )
        assertThat(request.useAlarmStream).isTrue()
    }

    @Test
    fun `music stream when external output and user prefers external`() {
        val request =
            build(
                settings = AppSettings(audioOutput = AudioOutputPreference.EXTERNAL),
                external = true,
            )
        assertThat(request.useAlarmStream).isFalse()
    }

    @Test
    fun `zero volume disables sound vibrate backup and tts`() {
        val request =
            build(
                alarm =
                    alarm(
                        volumeRatio = 0f,
                        vibrate = true,
                        backupEnabled = true,
                        timeTts = true,
                        memoTts = true,
                    ),
            )

        assertThat(request.useAlarmStream).isFalse()
        assertThat(request.volumeRatio).isEqualTo(0f)
        assertThat(request.vibrate).isFalse()
        assertThat(request.backupSound).isNull()
        assertThat(request.tts).isNull()
    }

    @Test
    fun `crescendo carried when enabled`() {
        val request =
            build(alarm = alarm(crescendo = CrescendoOption(enabled = true, seconds = 45)))
        assertThat(request.crescendo).isEqualTo(RingRequest.Crescendo(durationSeconds = 45))
    }

    @Test
    fun `crescendo off by default`() {
        assertThat(build().crescendo).isNull()
    }

    @Test
    fun `backup sound rings after 40 seconds when enabled`() {
        val request = build(alarm = alarm(backupEnabled = true))
        assertThat(request.backupSound).isNotNull()
        assertThat(request.backupSound!!.delaySeconds).isEqualTo(40)
        assertThat(request.backupSound.backupUri).isNotEmpty()
    }

    @Test
    fun `tts speaks memo and time with 60 second repeat`() {
        val request = build(alarm = alarm(timeTts = true, memoTts = true))
        val tts = request.tts!!
        assertThat(tts.speakLabel).isTrue()
        assertThat(tts.speakTime).isTrue()
        assertThat(tts.label).isEqualTo("take the bins out")
        assertThat(tts.repeatIntervalMillis).isEqualTo(60_000L)
    }

    @Test
    fun `tts only speaks the time when memo tts off`() {
        val request = build(alarm = alarm(timeTts = true, memoTts = false))
        assertThat(request.tts!!.speakLabel).isFalse()
        assertThat(request.tts.speakTime).isTrue()
    }

    @Test
    fun `no tts when nothing announces`() {
        assertThat(build(alarm = alarm(timeTts = false, memoTts = false)).tts).isNull()
    }

    @Test
    fun `audio uri falls back to the system default alarm when unset`() {
        val request = build(alarm = alarm(ringtoneUri = null))
        assertThat(request.audioUri).isEqualTo(RingRequestBuilder.DEFAULT_ALARM_URI)
        val custom = build(alarm = alarm(ringtoneUri = "content://media/alarms/42"))
        assertThat(custom.audioUri).isEqualTo("content://media/alarms/42")
    }

    @Test
    fun `the silent pseudo-uri rings without a main sound`() {
        val request =
            build(
                alarm =
                    alarm(ringtoneUri = dev.tomng.alarme.core.model.ringtone.RingtoneUris.SILENT),
            )
        assertThat(request.audioUri).isNull()
    }
}
