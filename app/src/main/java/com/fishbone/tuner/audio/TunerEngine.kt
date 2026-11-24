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
import kotlin.math.log2

/** Records locally only while enabled; microphone audio is never stored or transmitted. */
class TunerEngine(
    private val scope: CoroutineScope,
    private val onPitch: (Double?) -> Unit
) {
    private var job: Job? = null
    @Volatile private var recording: AudioRecord? = null

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val HOP = 4096
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                withContext(Dispatchers.Main) { onPitch(null) }
                return@launch
            }
            val recorder = createRecorder(minBuffer)
            if (recorder == null) {
                withContext(Dispatchers.Main) { onPitch(null) }
                return@launch
            }
            recording = recorder
            val detector = PitchDetector(SAMPLE_RATE)
            val frame = ShortArray(PitchDetector.WINDOW_SIZE)
            val hop = ShortArray(HOP)
            val recent = ArrayDeque<Double>()
            var filled = 0
            var missing = 0
            var inconsistent = 0
            try {
                recorder.startRecording()
                while (isActive) {
                    var offset = 0
                    while (offset < hop.size && isActive) {
                        val count = recorder.read(
                            hop, offset, hop.size - offset, AudioRecord.READ_BLOCKING
                        )
                        if (count <= 0) break
                        offset += count
                    }
                    if (offset < hop.size) continue

                    if (filled < frame.size) {
                        System.arraycopy(hop, 0, frame, filled, HOP)
                        filled += HOP
                        if (filled < frame.size) continue
                    } else {
                        System.arraycopy(frame, HOP, frame, 0, frame.size - HOP)
                        System.arraycopy(hop, 0, frame, frame.size - HOP, HOP)
                    }

                    val measured = detector.detect(frame)
                    if (measured == null) {
                        missing++
                        // One noisy microphone frame should not flicker the display.
                        if (missing >= 3) {
                            recent.clear()
                            inconsistent = 0
                            withContext(Dispatchers.Main) { onPitch(null) }
                        }
                        continue
                    }
                    missing = 0
                    val previous = recent.lastOrNull()
                    if (previous != null) {
                        val jumpCents = abs(1200.0 * log2(measured / previous))
                        if (jumpCents > 150.0) {
                            // Require a second consecutive non-harmonic candidate,
                            // but remain responsive to an actual string change.
                            inconsistent++
                            if (inconsistent < 2) continue
                            recent.clear()
                        } else {
                            inconsistent = 0
                        }
                    }

                    recent.addLast(measured)
                    if (recent.size > 4) recent.removeFirst()
                    val sorted = recent.sorted()
                    val stableHz = if (sorted.size % 2 == 0) {
                        (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                    } else {
                        sorted[sorted.size / 2]
                    }
                    withContext(Dispatchers.Main) { onPitch(stableHz) }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onPitch(null) }
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                recorder.release()
                if (recording === recorder) recording = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createRecorder(minBuffer: Int): AudioRecord? {
        val bytes = maxOf(minBuffer, PitchDetector.WINDOW_SIZE * 2 * 2)
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in sources) {
            val recorder = try {
                AudioRecord(
                    source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bytes
                )
            } catch (_: Exception) { null }
            if (recorder?.state == AudioRecord.STATE_INITIALIZED) return recorder
            recorder?.release()
        }
        return null
    }

    fun stop() {
        job?.cancel()
        job = null
        try { recording?.stop() } catch (_: Exception) {}
        onPitch(null)
    }
}
