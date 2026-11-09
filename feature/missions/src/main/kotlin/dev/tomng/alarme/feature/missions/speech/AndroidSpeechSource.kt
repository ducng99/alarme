package dev.tomng.alarme.feature.missions.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The platform speech recogniser, preferring the on-device engine where it exists so the mission
 * works offline. Results are the top hypothesis; errors map to "no speech" or "unavailable".
 */
class AndroidSpeechSource(private val context: Context) : SpeechSource {

    private val _events = MutableStateFlow<SpeechEvent>(SpeechEvent.Idle)
    override val events: StateFlow<SpeechEvent> = _events

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    private val listener =
        object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                listening = false
                val text =
                    results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                _events.value =
                    if (text.isEmpty()) SpeechEvent.NoSpeech else SpeechEvent.Recognized(text)
            }

            override fun onError(error: Int) {
                listening = false
                _events.value =
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechEvent.NoSpeech

                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> SpeechEvent.Unavailable

                        else -> SpeechEvent.Error
                    }
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

    override fun isLanguageAvailable(locale: String): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    override fun startListening(locale: String) {
        if (listening) return
        val engine =
            recognizer
                ?: createRecognizer().also {
                    recognizer = it
                    it?.setRecognitionListener(listener)
                }
        if (engine == null) {
            _events.value = SpeechEvent.Unavailable
            return
        }
        listening = true
        engine.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false),
        )
    }

    private fun createRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        return runCatching {
            if (
                android.os.Build.VERSION.SDK_INT >= 31 &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }
            .getOrNull()
    }

    override fun stopListening() {
        if (!listening) return
        listening = false
        recognizer?.stopListening()
    }

    override fun close() {
        listening = false
        recognizer?.destroy()
        recognizer = null
    }

    @Suppress("unused") private fun localeOf(tag: String): Locale = Locale.forLanguageTag(tag)
}
