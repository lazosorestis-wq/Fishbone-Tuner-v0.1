package com.fishbone.tuner.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Records microphone audio only while enabled; no audio is saved or transmitted. */
class TunerEngine(private val scope: CoroutineScope, private val onPitch: (Double?) -> Unit) {
    private var job: Job? = null
    @Volatile private var recording: AudioRecord? = null

    @SuppressLint("MissingPermission") // Activity verifies runtime RECORD_AUDIO permission.
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val sampleRate = 44100
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) { withContext(Dispatchers.Main) { onPitch(null) }; return@launch }
            val recorder = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 8192 * 2))
            } catch (_: Exception) { null }
            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder?.release()
                withContext(Dispatchers.Main) { onPitch(null) }
                return@launch
            }
            recording = recorder
            val detector = PitchDetector(sampleRate)
            val frame = ShortArray(4096)
            val history = ArrayDeque<Double>()
            try {
                recorder.startRecording()
                while (isActive) {
                    var offset = 0
                    while (offset < frame.size && isActive) {
                        val count = recorder.read(frame, offset, frame.size - offset, AudioRecord.READ_BLOCKING)
                        if (count <= 0) break
                        offset += count
                    }
                    if (offset < frame.size) continue
                    val pitch = detector.detect(frame)
                    if (pitch == null) {
                        history.clear()
                        withContext(Dispatchers.Main) { onPitch(null) }
                    } else {
                        if (history.isNotEmpty() && abs(12.0 * kotlin.math.log2(pitch / history.last())) > 2.0) history.clear()
                        history.addLast(pitch)
                        if (history.size > 5) history.removeFirst()
                        val stable = history.sorted()[history.size / 2]
                        withContext(Dispatchers.Main) { onPitch(stable) }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onPitch(null) }
            } finally {
                try { recorder.stop() } catch (_: Exception) { }
                recorder.release()
                if (recording === recorder) recording = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        // Stop unblocks READ_BLOCKING on most devices.
        try { recording?.stop() } catch (_: Exception) { }
        onPitch(null)
    }
}
