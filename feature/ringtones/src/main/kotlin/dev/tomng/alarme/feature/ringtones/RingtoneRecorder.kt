package dev.tomng.alarme.feature.ringtones

import android.content.Context
import android.media.MediaRecorder
import dev.tomng.alarme.core.data.repository.RingtoneRepository
import dev.tomng.alarme.core.data.ringtone.RingtonePaths
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import java.io.File
import kotlin.time.Clock

/** Mic capture seam; MediaRecorder in production, faked in tests. */
interface RecorderEngine {
    fun start(file: File)

    fun stop()
}

/** Voice memos for the RECORD ringtone source, captured straight into app storage. */
class RingtoneRecorder(
    private val paths: RingtonePaths,
    private val engine: RecorderEngine,
    private val probe: AudioProbe,
    private val catalog: RingtoneRepository,
) {
    private var current: File? = null

    fun start() {
        val name = "recording_${Clock.System.now().toEpochMilliseconds()}.m4a"
        val file = paths.targetFile(RingtoneKind.RECORD, name)
        current = file
        engine.start(file)
    }

    suspend fun stop(): RecordingResult {
        val file = current ?: return RecordingResult.NotRecording
        current = null
        runCatching { engine.stop() }
            .onFailure {
                file.delete()
                return RecordingResult.Failed
            }
        val probed = probe.probe(file)
        if (!probed.decodable || probed.durationMs < MIN_DURATION_MS) {
            file.delete()
            return RecordingResult.TooShort
        }
        val uri = android.net.Uri.fromFile(file).toString()
        val title = "Recording ${file.nameWithoutExtension.substringAfterLast('_')}"
        catalog.insert(RingtoneKind.RECORD, uri, title)
        return RecordingResult.Recorded(uri, title)
    }

    sealed interface RecordingResult {
        data class Recorded(val uri: String, val title: String) : RecordingResult

        data object TooShort : RecordingResult

        data object NotRecording : RecordingResult

        data object Failed : RecordingResult
    }

    companion object {
        /** Sub-second taps are discarded — they are almost always accidents. */
        const val MIN_DURATION_MS: Long = 1_000
    }
}

/** MediaRecorder-backed engine writing AAC in an MPEG-4 container. */
class MediaRecorderEngine(private val context: Context) : RecorderEngine {

    private var recorder: MediaRecorder? = null

    override fun start(file: File) {
        val mediaRecorder = MediaRecorder(context)
        recorder = mediaRecorder
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioEncodingBitRate(96_000)
        mediaRecorder.setAudioSamplingRate(44_100)
        mediaRecorder.setOutputFile(file.absolutePath)
        mediaRecorder.prepare()
        mediaRecorder.start()
    }

    override fun stop() {
        val mediaRecorder = recorder ?: return
        recorder = null
        runCatching { mediaRecorder.stop() }
        mediaRecorder.release()
    }
}
