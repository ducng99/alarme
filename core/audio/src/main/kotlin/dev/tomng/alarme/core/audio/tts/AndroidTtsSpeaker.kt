package dev.tomng.alarme.core.audio.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Speaks one round of announcements sequentially and suspends until the queue drains. We use the
 * platform engine directly, which matches the observable behaviour (label + time, repeated on an
 * interval).
 */
interface TtsSpeaker {
    /** Speaks [utterances] in order; suspends until every utterance finished (or failed). */
    suspend fun speak(utterances: List<String>)

    /** Immediately drops anything still queued. */
    fun stop()
}

@Singleton
class AndroidTtsSpeaker @Inject constructor(@ApplicationContext private val context: Context) :
    TtsSpeaker {

    private var tts: TextToSpeech? = null

    @Volatile private var ready = false
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()

    private fun ensureEngine(onReady: () -> Unit) {
        if (ready) {
            onReady()
            return
        }
        if (tts != null) return // init in flight; the caller skips this round
        tts =
            TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    tts?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit

                            override fun onDone(utteranceId: String?) {
                                utteranceId?.let { pending.remove(it) }?.let(::resumeQuietly)
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                utteranceId?.let { pending.remove(it) }?.let(::resumeQuietly)
                            }
                        },
                    )
                    ready = true
                    onReady()
                }
            }
    }

    override suspend fun speak(utterances: List<String>) {
        if (utterances.isEmpty()) return
        // Engine init is async; if it hasn't completed yet this round is skipped rather than
        // blocking the ring (the interval will retry).
        val initialised = CompletableDeferred<Unit>()
        ensureEngine { initialised.complete(Unit) }
        withTimeoutOrNull(1_000) { initialised.await() } ?: return
        for (utterance in utterances) {
            val id = UUID.randomUUID().toString()
            suspendCancellableCoroutine { continuation ->
                pending[id] = continuation
                val result =
                    tts?.speak(utterance, TextToSpeech.QUEUE_ADD, null, id) ?: TextToSpeech.ERROR
                if (result != TextToSpeech.SUCCESS) {
                    pending.remove(id)
                    resumeQuietly(continuation)
                }
            }
        }
    }

    override fun stop() {
        pending.values.forEach { it.cancel() }
        pending.clear()
        tts?.stop()
    }

    private fun resumeQuietly(continuation: CancellableContinuation<Unit>) {
        continuation.resume(Unit) { _, _, _ -> }
    }
}
