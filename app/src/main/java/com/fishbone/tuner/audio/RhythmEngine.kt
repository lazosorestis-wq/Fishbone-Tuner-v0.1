package com.fishbone.tuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class RhythmMode { METRONOME, DRUMS }

/** Each groove is a one-bar, 16-step (sixteenth-note) drum pattern. */
data class DrumGroove(
    val name: String,
    val genre: String,
    val description: String,
    val suggestedBpm: Int,
    val kick: Set<Int>,
    val snare: Set<Int>,
    val hat: Set<Int>,
    val openHat: Set<Int> = emptySet(),
    val swing: Int = 0
)

object DrumGrooves {
    private fun hits(vararg steps: Int) = steps.toSet()
    val all = listOf(
        DrumGroove("Straight Rock", "ROCK", "Solid backbeat", 110,
            hits(0, 8, 10), hits(4, 12), hits(0, 2, 4, 6, 8, 10, 12, 14)),
        DrumGroove("Hard Rock", "ROCK", "Driving eighth notes", 135,
            hits(0, 6, 8, 10, 14), hits(4, 12), hits(0, 2, 4, 6, 8, 10, 12, 14), hits(14)),
        DrumGroove("Metal Gallop", "METAL", "Galloping kick pattern", 160,
            hits(0, 2, 3, 6, 8, 10, 11, 14), hits(4, 12),
            hits(0, 2, 4, 6, 8, 10, 12, 14)),
        DrumGroove("Double Kick", "METAL", "Fast double-bass pulse", 180,
            hits(0, 2, 4, 6, 8, 10, 12, 14), hits(4, 12),
            hits(0, 2, 4, 6, 8, 10, 12, 14), hits(14)),
        DrumGroove("Heavy Stoner", "STONER", "Slow, heavy half-time", 78,
            hits(0, 7, 10), hits(8), hits(0, 2, 4, 6, 8, 10, 12, 14), hits(14)),
        DrumGroove("Desert Groove", "STONER", "Loose desert-rock feel", 92,
            hits(0, 6, 8, 11, 14), hits(4, 12), hits(0, 2, 4, 6, 8, 10, 12, 14), swing = 12),
        DrumGroove("Blues Shuffle", "BLUES", "Triplet shuffle feel", 96,
            hits(0, 7, 8, 15), hits(4, 12), hits(0, 2, 4, 6, 8, 10, 12, 14), swing = 33),
        DrumGroove("Slow Blues", "BLUES", "Laid-back blues", 68,
            hits(0, 8, 11), hits(4, 12), hits(0, 2, 4, 6, 8, 10, 12, 14), swing = 30),
        DrumGroove("Doom Crawl", "DOOM", "Crushing, spacious half-time", 56,
            hits(0, 7, 10), hits(8), hits(0, 4, 8, 12), hits(14)),
        DrumGroove("Sludge Trudge", "SLUDGE", "Lurching slow groove", 68,
            hits(0, 3, 10, 14), hits(8), hits(0, 2, 4, 6, 8, 10, 12, 14)),
        DrumGroove("Thrash Drive", "METAL", "Fast snare-driven thrash", 196,
            hits(0, 3, 4, 7, 8, 11, 12, 15), hits(2, 6, 10, 14),
            hits(0, 2, 4, 6, 8, 10, 12, 14)),
        DrumGroove("Blues Rock", "BLUES", "Strong walking blues-rock pulse", 116,
            hits(0, 7, 8, 11), hits(4, 12), hits(0, 2, 4, 6, 8, 10, 12, 14),
            swing = 15)
    )
}

data class RhythmSettings(
    val mode: RhythmMode = RhythmMode.METRONOME,
    val bpm: Int = 100,
    val meter: Int = 4, // 3/4, 4/4, or six eighth-note pulses in 6/8.
    val groove: DrumGroove = DrumGrooves.all.first(),
    val swing: Int = 0, // Percentage of eighth-note shuffle; 33 = approx. 2:1.
    val volume: Float = 0.7f
)

/**
 * Fully offline PCM drum synthesis. Each bar is rendered and streamed to AudioTrack;
 * changes to tempo, groove, meter, or volume take effect at the next bar boundary.
 * Audio playback stops when this engine is disposed or the app leaves the foreground.
 */
class RhythmEngine(private val scope: CoroutineScope) {
    companion object { private const val SAMPLE_RATE = 44100 }
    @Volatile private var settings = RhythmSettings()
    @Volatile private var audioTrack: AudioTrack? = null
    private var job: Job? = null

    private val kick = synthKick()
    private val snare = synthSnare()
    private val closedHat = synthHat(false)
    private val openHat = synthHat(true)
    private val click = synthClick(false)
    private val accentClick = synthClick(true)

    fun update(next: RhythmSettings) { settings = next }

    fun start(next: RhythmSettings) {
        stop()
        settings = next
        job = scope.launch(Dispatchers.IO) { streamAudio() }
    }

    fun stop() {
        job?.cancel()
        job = null
        val old = audioTrack
        audioTrack = null
        try { old?.pause() } catch (_: Exception) {}
        try { old?.flush() } catch (_: Exception) {}
        try { old?.release() } catch (_: Exception) {}
    }

    private suspend fun streamAudio() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) return
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBytes, 16384))
                .build()
        } catch (_: Exception) { return }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return
        }
        audioTrack = track
        try {
            track.play()
            while (currentCoroutineContext().isActive) {
                val bar = renderBar(settings)
                var offset = 0
                while (offset < bar.size && currentCoroutineContext().isActive) {
                    val length = minOf(2048, bar.size - offset)
                    val written = track.write(bar, offset, length, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) return
                    offset += written
                }
            }
        } catch (_: Exception) {
            // Audio output can disappear (headphones disconnected, etc.).
        } finally {
            if (audioTrack === track) audioTrack = null
            try { track.stop() } catch (_: Exception) {}
            try { track.release() } catch (_: Exception) {}
        }
    }

    private fun renderBar(input: RhythmSettings): ShortArray {
        val bpm = input.bpm.coerceIn(40, 240)
        val framesPerBeat = SAMPLE_RATE * 60.0 / bpm
        val beats = if (input.mode == RhythmMode.METRONOME) input.meter.coerceIn(2, 6) else 4
        val frames = (beats * framesPerBeat).roundToInt().coerceAtLeast(1)
        val mix = FloatArray(frames)
        if (input.mode == RhythmMode.METRONOME) {
            for (beat in 0 until beats) {
                val sample = if (beat == 0 || (beats == 6 && beat == 3)) accentClick else click
                addHit(mix, (beat * framesPerBeat).roundToInt(), sample, 0.75f)
            }
        } else {
            val groove = input.groove
            val swing = input.swing.coerceIn(0, 45) / 100.0
            var position = 0.0
            for (step in 0 until 16) {
                val at = position.roundToInt()
                if (step in groove.kick) addHit(mix, at, kick, 0.98f)
                if (step in groove.snare) addHit(mix, at, snare, 0.85f)
                if (step in groove.openHat) addHit(mix, at, openHat, 0.48f)
                else if (step in groove.hat) addHit(mix, at, closedHat, 0.40f)
                position += framesPerBeat / 4.0 * (if (step % 4 < 2) 1.0 + swing else 1.0 - swing)
            }
        }
        val volume = input.volume.coerceIn(0f, 1f)
        return ShortArray(frames) { i ->
            val x = mix[i]
            val limited = (x / (1f + abs(x) * 0.48f) * volume).coerceIn(-1f, 1f)
            (limited * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Wraps tails to the next loop iteration so the final open hat isn't cut off. */
    private fun addHit(mix: FloatArray, start: Int, sample: FloatArray, gain: Float) {
        for (i in sample.indices) {
            val index = (start + i) % mix.size
            mix[index] += sample[i] * gain
        }
    }

    private fun synthKick(): FloatArray {
        val frames = (SAMPLE_RATE * 0.35).toInt()
        var phase = 0.0
        return FloatArray(frames) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val hz = 52.0 + 125.0 * exp(-t * 27)
            phase += 2 * PI * hz / SAMPLE_RATE
            (sin(phase) * exp(-t * 14)).toFloat()
        }
    }

    private fun synthSnare(): FloatArray {
        val frames = (SAMPLE_RATE * 0.23).toInt()
        val rng = Random(2026)
        var phase = 0.0
        var previousNoise = 0.0
        return FloatArray(frames) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val noise = rng.nextDouble(-1.0, 1.0)
            val high = noise - previousNoise * 0.76
            previousNoise = noise
            phase += 2 * PI * 180.0 / SAMPLE_RATE
            ((0.67 * high + 0.33 * sin(phase)) * exp(-t * 19)).toFloat()
        }
    }

    private fun synthHat(open: Boolean): FloatArray {
        val frames = (SAMPLE_RATE * if (open) 0.36 else 0.085).toInt()
        val rng = Random(if (open) 91 else 90)
        var low = 0.0
        return FloatArray(frames) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val noise = rng.nextDouble(-1.0, 1.0)
            low = low * 0.91 + noise * 0.09
            val envelope = exp(-t * if (open) 13 else 62)
            ((noise - low) * envelope).toFloat()
        }
    }

    private fun synthClick(accent: Boolean): FloatArray {
        val frames = (SAMPLE_RATE * 0.065).toInt()
        val frequency = if (accent) 1450.0 else 950.0
        return FloatArray(frames) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            (sin(2 * PI * frequency * t) * exp(-t * 68)).toFloat()
        }
    }
}
