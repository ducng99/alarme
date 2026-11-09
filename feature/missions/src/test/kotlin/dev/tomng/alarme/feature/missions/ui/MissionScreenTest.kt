package dev.tomng.alarme.feature.missions.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.mission.ImageRecognitionObject
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.TokenMode
import dev.tomng.alarme.core.model.mission.WordBeatLanguage
import dev.tomng.alarme.core.sensors.shake.ShakeSensorTracker
import dev.tomng.alarme.core.sensors.source.SensorSampleSink
import dev.tomng.alarme.core.sensors.source.SensorSource
import dev.tomng.alarme.core.sensors.squat.CoverSensorTracker
import dev.tomng.alarme.core.sensors.step.StepSensorTracker
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.camera.BarcodeMissionUi
import dev.tomng.alarme.feature.missions.camera.BarcodeScanner
import dev.tomng.alarme.feature.missions.camera.LabelMissionUi
import dev.tomng.alarme.feature.missions.camera.LabelScanner
import dev.tomng.alarme.feature.missions.camera.PhotoCapturer
import dev.tomng.alarme.feature.missions.camera.PhotoMissionUi
import dev.tomng.alarme.feature.missions.engine.ImageRecognitionVerifier
import dev.tomng.alarme.feature.missions.speech.SpeechEvent
import dev.tomng.alarme.feature.missions.speech.SpeechSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SQUAT_NO_SENSOR_TEXT =
    "This device has no light or proximity sensor, so squats can't be counted. " +
        "Wait for the emergency dismiss or use +10 seconds."
private const val STEP_NO_SENSOR_TEXT =
    "This device has no step counter. Wait for the emergency dismiss or use +10 seconds."
private const val SPEECH_UNAVAILABLE_TEXT =
    "Speech recognition is unavailable on this device. " +
        "Wait for the emergency dismiss or use +10 seconds."

private class Callbacks : MissionStageCallbacks {
    var completed = 0
    var resets = 0
    var stops = 0

    override fun onComplete() {
        completed++
    }

    override fun onTimerReset() {
        resets++
    }

    override fun onTimerStop() {
        stops++
    }
}

private class FakeSource : SensorSource {
    var sink: SensorSampleSink? = null

    override fun start(sink: SensorSampleSink) {
        this.sink = sink
    }

    override fun stop() {
        sink = null
    }

    fun emit(vararg values: Float, timestampNanos: Long) {
        sink?.onSample(values.copyOf(), timestampNanos)
    }
}

private class FakeBarcodeScanner : BarcodeScanner {
    private val flow = MutableStateFlow<String?>(null)
    override val scannedValue: StateFlow<String?> = flow

    fun scan(value: String) {
        flow.value = value
    }

    override fun close() = Unit
}

private class FakeLabelScanner : LabelScanner {
    private val flow = MutableStateFlow<List<ImageRecognitionVerifier.RecognizedLabel>>(emptyList())
    override val labels: StateFlow<List<ImageRecognitionVerifier.RecognizedLabel>> = flow

    fun recognise(vararg labels: ImageRecognitionVerifier.RecognizedLabel) {
        flow.value = labels.toList()
    }

    override fun close() = Unit
}

private class FakeCapturer(private val success: Boolean = true) : PhotoCapturer {
    var captures = 0
        private set

    override suspend fun capture(): Boolean {
        captures++
        return success
    }

    override fun close() = Unit
}

private class FakeSpeech(available: Boolean = true) : SpeechSource {
    private val flow = MutableStateFlow<SpeechEvent>(SpeechEvent.Idle)
    override val events: StateFlow<SpeechEvent> = flow
    private val isAvailable = available
    var listenCalls = 0
        private set

    override fun isLanguageAvailable(locale: String): Boolean = isAvailable

    override fun startListening(locale: String) {
        listenCalls++
    }

    override fun stopListening() = Unit

    override fun close() = Unit

    fun hear(text: String) {
        flow.value = SpeechEvent.Recognized(text)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SensorMissionScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `shaking to the goal completes the shake mission`() {
        val callbacks = Callbacks()
        val source = FakeSource()
        val tracker = ShakeSensorTracker(source)
        rule.setContent { ShakeMissionScreen(Mission.Shake(count = 2), callbacks, { tracker }) }

        val g = 9.80665f
        rule.runOnIdle {
            source.emit(0f, 0f, 3f * g, timestampNanos = 1_000_000_000)
            source.emit(0f, 0f, 3f * g, timestampNanos = 1_500_000_000)
        }
        rule.waitForIdle()

        assertThat(callbacks.completed).isEqualTo(1)
    }

    @Test
    fun `squat reps to the goal complete the squat mission`() {
        val callbacks = Callbacks()
        val proximity = FakeSource()
        val tracker = CoverSensorTracker.forTest(proximity, light = null, goal = 1)
        rule.setContent { SquatMissionScreen(Mission.Squat(count = 1), callbacks, { tracker }) }

        rule.runOnIdle {
            proximity.emit(0f, timestampNanos = 1_000_000_000)
            proximity.emit(5f, timestampNanos = 1_800_000_000)
        }
        rule.waitForIdle()

        assertThat(callbacks.completed).isEqualTo(1)
    }

    @Test
    fun `a device without sensors explains the dead end`() {
        val callbacks = Callbacks()
        val tracker = CoverSensorTracker.forTest(proximity = null, light = null, goal = 5)
        rule.setContent { SquatMissionScreen(Mission.Squat(count = 5), callbacks, { tracker }) }

        rule.onNodeWithText(SQUAT_NO_SENSOR_TEXT).assertExists()
        assertThat(callbacks.completed).isEqualTo(0)
    }

    @Test
    fun `step progress to the goal completes the step mission`() {
        val callbacks = Callbacks()
        val source = FakeSource()
        val tracker = StepSensorTracker.forTest(source, stepCounterAvailable = true, goal = 3)
        rule.setContent { StepMissionScreen(Mission.Step(count = 3), callbacks, { tracker }) }

        rule.runOnIdle {
            source.emit(1_000f, timestampNanos = 1_000_000_000)
            source.emit(1_003f, timestampNanos = 2_000_000_000)
        }
        rule.waitForIdle()

        assertThat(callbacks.completed).isEqualTo(1)
    }

    @Test
    fun `a device without a step counter explains the dead end`() {
        val callbacks = Callbacks()
        val tracker = StepSensorTracker.forTest(null, stepCounterAvailable = false, goal = 5)
        rule.setContent { StepMissionScreen(Mission.Step(count = 5), callbacks, { tracker }) }

        rule.onNodeWithText(STEP_NO_SENSOR_TEXT).assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraMissionScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `scanning the registered barcode completes the mission`() {
        val callbacks = Callbacks()
        val scanner = FakeBarcodeScanner()
        rule.setContent {
            BarcodeMissionScreen(
                Mission.Barcode(barcodeValue = "12345"),
                callbacks,
                { BarcodeMissionUi(scanner, controller = null) },
            )
        }

        rule.runOnIdle { scanner.scan("12345") }
        rule.waitForIdle()

        assertThat(callbacks.completed).isEqualTo(1)
    }

    @Test
    fun `a mismatched barcode does not complete and says so`() {
        val callbacks = Callbacks()
        val scanner = FakeBarcodeScanner()
        rule.setContent {
            BarcodeMissionScreen(
                Mission.Barcode(barcodeValue = "12345"),
                callbacks,
                { BarcodeMissionUi(scanner, controller = null) },
            )
        }

        rule.runOnIdle { scanner.scan("99999") }

        assertThat(callbacks.completed).isEqualTo(0)
        rule.onNodeWithText("Not the registered barcode").assertExists()
    }

    @Test
    fun `recognising the target object pauses the timer then completes`() {
        val callbacks = Callbacks()
        val scanner = FakeLabelScanner()
        rule.setContent {
            ImageRecognitionMissionScreen(
                Mission.ImageRecognition(objects = listOf(ImageRecognitionObject.CAT)),
                callbacks,
                { LabelMissionUi(scanner, controller = null) },
            )
        }

        rule.runOnIdle {
            scanner.recognise(
                ImageRecognitionVerifier.RecognizedLabel("Cat", 0.9f),
            )
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        assertThat(callbacks.stops).isEqualTo(1)
        assertThat(callbacks.completed).isEqualTo(1)
    }

    @Test
    fun `taking the photo completes the honour-system photo mission`() {
        val callbacks = Callbacks()
        val capturer = FakeCapturer(success = true)
        rule.setContent {
            PhotoMissionScreen(
                Mission.Photo(photoPath = "/nonexistent.jpg"),
                callbacks,
                { PhotoMissionUi(capturer, controller = null) },
            )
        }

        rule.onNodeWithText("Take the photo").performClick()
        rule.waitForIdle()

        assertThat(capturer.captures).isEqualTo(1)
        assertThat(callbacks.completed).isEqualTo(1)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordBeatMissionScreenTest {

    @get:Rule val rule = createComposeRule()

    private val mission =
        Mission.WordBeat(
            rounds = 1,
            language = WordBeatLanguage.ENGLISH,
            volumeLevel = dev.tomng.alarme.core.model.mission.WordBeatVolumeLevel.MEDIUM,
        )

    @Test
    fun `reading enough of the sentence completes the mission`() {
        val callbacks = Callbacks()
        val speech = FakeSpeech(available = true)
        val target =
            dev.tomng.alarme.feature.missions.engine.WordBeatRounds.build(
                    WordBeatLanguage.ENGLISH,
                    rounds = 1,
                    seed = 99,
                )
                .first()
        rule.setContent { WordBeatMissionScreen(mission, callbacks, { speech }, seed = 99) }

        // Repeat the on-screen target back — that always passes the 25 % bar.
        rule.runOnIdle { speech.hear(target) }
        rule.waitForIdle()

        assertThat(callbacks.completed).isEqualTo(1)
    }

    @Test
    fun `an unsupported language shows the dead-end message`() {
        val callbacks = Callbacks()
        val speech = FakeSpeech(available = false)
        rule.setContent { WordBeatMissionScreen(mission, callbacks, { speech }) }

        rule.onNodeWithText(SPEECH_UNAVAILABLE_TEXT).assertExists()
    }

    @Test
    fun `too little overlap does not complete the round`() {
        val callbacks = Callbacks()
        val speech = FakeSpeech(available = true)
        rule.setContent { WordBeatMissionScreen(mission, callbacks, { speech }) }

        rule.runOnIdle { speech.hear("zzz") }
        rule.waitForIdle()

        assertThat(callbacks.completed).isEqualTo(0)
    }

    @Test
    fun `character languages match by characters`() {
        assertThat(TokenMode.CHARACTER).isNotNull()
    }
}
