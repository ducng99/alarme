package dev.tomng.alarme.core.audio.host

import android.content.Context
import android.media.AudioManager
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.ring.RingRequest
import dev.tomng.alarme.core.audio.player.AlarmPlayer
import dev.tomng.alarme.core.audio.player.AlarmPlayerFactory
import dev.tomng.alarme.core.audio.tts.TtsSpeaker
import dev.tomng.alarme.core.common.time.FakeAppClock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The ring orchestrator: stream choice, volume lock, audio focus, main player with crescendo,
 * backup escalation (max volume at +delay), and the TTS interplay (pause main, duck to <= max/2,
 * resume, repeat — but no repeat once a backup sound is configured).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingAudioHostTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val clock =
        FakeAppClock(Instant.parse("2026-11-04T06:00:00Z"), TimeZone.UTC) // 06:00 local
    private val players = RecordingPlayerFactory()
    private val speaker = RecordingSpeaker()
    private var host: RingAudioHost? = null

    @Before
    fun setUp() {
        // Distinct defaults so assertions cannot pass by coincidence.
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 2, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 2, 0)
    }

    @After
    fun tearDown() {
        host?.stop()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.newHost(): RingAudioHost =
        RingAudioHost(
                context = context,
                scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
                clock = clock,
                players = players,
                speaker = speaker,
            )
            .also { host = it }

    private fun request(
        useAlarmStream: Boolean = true,
        volumeRatio: Float = 1f,
        crescendo: RingRequest.Crescendo? = null,
        backup: RingRequest.BackupSound? = null,
        tts: RingRequest.Tts? = null,
        vibrate: Boolean = false,
        uri: String = "content://media/alarm/one",
    ) =
        RingRequest(
            audioUri = uri,
            useAlarmStream = useAlarmStream,
            volumeRatio = volumeRatio,
            crescendo = crescendo,
            backupSound = backup,
            tts = tts,
            vibrate = vibrate,
        )

    private fun volume(stream: Int) = audioManager.getStreamVolume(stream)

    @Test
    fun `plain ring forces alarm stream to target and plays the main sound`() = runTest {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val host = newHost()

        host.start(request(volumeRatio = 0.5f))

        assertThat(volume(AudioManager.STREAM_ALARM))
            .isEqualTo(
                dev.tomng.alarme.core.audio.volume.targetVolume(max, 0.5f),
            )
        val main = players.main
        assertThat(main.started).isTrue()
        assertThat(main.uri).isEqualTo("content://media/alarm/one")
        assertThat(main.usageAlarm).isTrue()
        assertThat(main.volumeGain).isEqualTo(1f)
        assertThat(speaker.rounds).isEmpty()

        host.stop()
        assertThat(volume(AudioManager.STREAM_ALARM)).isEqualTo(2) // restored
        assertThat(main.released).isTrue()
    }

    @Test
    fun `external routing plays on the music stream with media usage`() = runTest {
        val host = newHost()

        host.start(request(useAlarmStream = false, volumeRatio = 1f))

        assertThat(players.main.usageAlarm).isFalse()
        assertThat(volume(AudioManager.STREAM_MUSIC))
            .isEqualTo(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))

        host.stop()
    }

    @Test
    fun `crescendo ramps the player gain quadratically to full`() = runTest {
        val host = newHost()
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val ratio = 0.5f // floors to 0.065 (ratio band [0.5, 0.7))

        host.start(
            request(volumeRatio = ratio, crescendo = RingRequest.Crescendo(durationSeconds = 10)),
        )

        val firstGain = players.main.volumeGain
        assertThat(firstGain).isEqualTo(0.065f)

        advanceTimeBy(5_001)
        assertThat(players.main.volumeGain).isWithin(1e-4f).of(0.25f)

        advanceTimeBy(5_100)
        assertThat(players.main.volumeGain).isEqualTo(1f)

        host.stop()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `backup sound stops the main player and forces maximum after the delay`() = runTest {
        val host = newHost()
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        host.start(
            request(
                volumeRatio = 0.5f,
                backup = RingRequest.BackupSound(backupUri = "asset://backup", delaySeconds = 40),
            ),
        )

        advanceTimeBy(39_999)
        assertThat(players.main.paused).isFalse()
        assertThat(players.backup).isNull()

        advanceTimeBy(2)
        assertThat(players.main.paused).isTrue()
        assertThat(players.backup!!.started).isTrue()
        assertThat(players.backup!!.uri).isEqualTo("asset://backup")
        assertThat(volume(AudioManager.STREAM_ALARM)).isEqualTo(max)

        host.stop()
        assertThat(volume(AudioManager.STREAM_ALARM)).isEqualTo(2)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `tts pauses main ducks to at most half then resumes and repeats`() = runTest {
        val host = newHost()
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val ducked =
            dev.tomng.alarme.core.audio.volume.targetVolume(max, 0.5f).coerceAtMost(max / 2)

        host.start(
            request(
                volumeRatio = 0.5f,
                tts =
                    RingRequest.Tts(
                        label = "stand up",
                        speakLabel = true,
                        speakTime = true,
                        repeatIntervalMillis = 60_000,
                    ),
            ),
        )

        advanceTimeBy(3_001) // first round at +3 s
        assertThat(speaker.rounds).hasSize(1)
        assertThat(speaker.rounds.single()).containsExactly("stand up", "6:00 AM").inOrder()
        assertThat(players.main.paused).isTrue() // paused while speaking
        assertThat(volume(AudioManager.STREAM_ALARM)).isEqualTo(ducked)

        speaker.finishRound()
        testScheduler.runCurrent() // resume + finally; the repeat delay stays pending
        assertThat(players.main.paused).isFalse()
        assertThat(volume(AudioManager.STREAM_ALARM))
            .isEqualTo(dev.tomng.alarme.core.audio.volume.targetVolume(max, 0.5f))

        advanceTimeBy(60_001)
        assertThat(speaker.rounds).hasSize(2)

        host.stop()
        assertThat(players.main.released).isTrue()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `tts does not repeat when a backup sound is configured`() = runTest {
        val host = newHost()

        host.start(
            request(
                volumeRatio = 1f,
                backup = RingRequest.BackupSound(backupUri = "asset://backup", delaySeconds = 30),
                tts =
                    RingRequest.Tts(
                        label = "",
                        speakLabel = false,
                        speakTime = true,
                        repeatIntervalMillis = 10_000,
                    ),
            ),
        )

        advanceTimeBy(3_001)
        speaker.finishRound()
        advanceUntilIdle()
        advanceTimeBy(30_001)
        assertThat(speaker.rounds).hasSize(1)

        host.stop()
    }

    @Test
    fun `vibration starts with the ring and stops with it`() = runTest {
        val host = newHost()
        val vibrator = context.getSystemService(Vibrator::class.java)

        host.start(request(vibrate = true))
        assertThat(shadowOf(vibrator).isVibrating).isTrue()

        host.stop()
        assertThat(shadowOf(vibrator).isVibrating).isFalse()
    }

    @Test
    fun `a zero volume ring is silent`() = runTest {
        val host = newHost()

        host.start(request(volumeRatio = 0f))

        assertThat(players.created).isEmpty()
        assertThat(volume(AudioManager.STREAM_ALARM)).isEqualTo(2)

        host.stop()
        assertThat(players.created).isEmpty()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `starting a second ring replaces the first`() = runTest {
        val host = newHost()

        host.start(request(uri = "content://media/alarm/one"))
        val first = players.created[0]

        host.start(request(uri = "content://media/alarm/two"))
        val second = players.created[1]

        assertThat(first.released).isTrue()
        assertThat(second.uri).isEqualTo("content://media/alarm/two")
        assertThat(players.created).hasSize(2)

        host.stop()
    }

    class RecordingPlayer : AlarmPlayer {
        var uri: String? = null
        var usageAlarm = true
        var started = false
        var paused = false
        var released = false
        var volumeGain = 1f

        override fun prepare(uri: String, usageAlarm: Boolean, repeatOne: Boolean) {
            this.uri = uri
            this.usageAlarm = usageAlarm
        }

        override fun play() {
            started = true
            paused = false
        }

        override fun pause() {
            paused = true
        }

        override fun setVolume(gain: Float) {
            volumeGain = gain
        }

        override fun release() {
            released = true
        }
    }

    class RecordingPlayerFactory : AlarmPlayerFactory {
        val created = mutableListOf<RecordingPlayer>()
        val main: RecordingPlayer
            get() = created[0]

        val backup: RecordingPlayer?
            get() = created.getOrNull(1)

        override fun create(): AlarmPlayer = RecordingPlayer().also { created += it }
    }

    class RecordingSpeaker : TtsSpeaker {
        val rounds = mutableListOf<List<String>>()
        var completedRounds = 0
            private set

        private var gate: CompletableDeferred<Unit>? = null

        override suspend fun speak(utterances: List<String>) {
            rounds += utterances
            gate = CompletableDeferred()
            gate!!.await()
            completedRounds++
        }

        override fun stop() = Unit

        fun finishRound() {
            gate?.complete(Unit)
            gate = null
        }
    }
}
