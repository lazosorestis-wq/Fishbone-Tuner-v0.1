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
import kotlin.math.cos
import kotlin.math.pow
import kotlin.random.Random

enum class RhythmMode { METRONOME, DRUMS }

enum class DrumKit(val label: String) {
    ROCK("Studio Rock"), METAL("Heavy Rock"), STONER("Doom / Stoner")
}

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
    val volume: Float = 0.7f,
    val kit: DrumKit = DrumKit.ROCK
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

    // Three independent acoustic-modeled hits per instrument: real velocity
    // differences no longer just replay the identical PCM sample on every beat.
    private val kickSamples = Array(3) { synthKick(it) }
    private val snareSamples = Array(3) { synthSnare(it) }
    private val closedHatSamples = Array(3) { synthHat(false, it) }
    private val openHatSamples = Array(3) { synthHat(true, it) }
    private val crashSamples = Array(2) { synthCymbal(false, it) }
    private val rideSamples = Array(2) { synthCymbal(true, it) }
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
            var barNumber = 0
            while (currentCoroutineContext().isActive) {
                val bar = renderBar(settings, barNumber++)
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

    private fun renderBar(input: RhythmSettings, barNumber: Int): ShortArray {
        val bpm = input.bpm.coerceIn(40, 240)
        val framesPerBeat = SAMPLE_RATE * 60.0 / bpm
        val beats = if (input.mode == RhythmMode.METRONOME) input.meter.coerceIn(2, 6) else 4
        val frames = (beats * framesPerBeat).roundToInt().coerceAtLeast(1)
        val mix = FloatArray(frames)

        if (input.mode == RhythmMode.METRONOME) {
            for (beat in 0 until beats) {
                val sample = if (beat == 0 || (beats == 6 && beat == 3)) accentClick else click
                addHit(mix, (beat * framesPerBeat).roundToInt(), sample, 0.8f)
            }
        } else {
            val groove = input.groove
            val swing = input.swing.coerceIn(0, 45) / 100.0
            val humanizer = Random(barNumber * 7561 + groove.name.hashCode())
            val kit = input.kit
            val kickTone = when (kit) {
                DrumKit.ROCK -> 0.84f
                DrumKit.METAL -> 0.99f
                DrumKit.STONER -> 0.94f
            }
            val snareTone = when (kit) {
                DrumKit.ROCK -> 0.70f
                DrumKit.METAL -> 0.91f
                DrumKit.STONER -> 0.72f
            }
            // Fill the first beat of every four bars with a broad cymbal.
            if (barNumber % 4 == 0) {
                addHit(mix, 0, crashSamples[barNumber % 2], 0.25f)
            }
            var stepPosition = 0.0
            for (step in 0 until 16) {
                val jitterFrames = (humanizer.nextInt(-3, 4) * SAMPLE_RATE / 1000.0).roundToInt()
                val at = (stepPosition.roundToInt() + jitterFrames).coerceAtLeast(0)
                val variation = (step + barNumber) % 3
                val velocity = 0.92f + humanizer.nextFloat() * 0.15f
                val downBeat = step % 4 == 0
                if (step in groove.kick) {
                    val kickGain = if (downBeat) 1.08f else 0.91f
                    addHit(mix, at, kickSamples[variation], 0.81f * kickGain * velocity * kickTone)
                }
                if (step in groove.snare) {
                    val snareGain = if (step == 4 || step == 12 || step == 8) 1f else 0.73f
                    addHit(mix, at, snareSamples[variation], snareGain * snareTone * velocity)
                }
                if (step in groove.openHat) {
                    addHit(mix, at, openHatSamples[variation], 0.20f * velocity)
                } else if (step in groove.hat) {
                    val hatGain = if (downBeat) 0.19f else 0.13f
                    addHit(mix, at, closedHatSamples[variation], hatGain * velocity)
                }
                if (step == 12 && groove.genre == "METAL" && barNumber % 2 == 1) {
                    addHit(mix, at, rideSamples[barNumber % 2], 0.08f)
                }
                // Short, quiet snare-wire ghost notes in selected grooves.
                if (step == 15 && groove.genre in listOf("ROCK","STONER","BLUES")
                    && barNumber % 2 == 1) {
                    addHit(mix, at, snareSamples[variation], 0.09f)
                }
                stepPosition += framesPerBeat / 4.0 *
                    (if (step % 4 < 2) 1.0 + swing else 1.0 - swing)
            }
        }

        // Tiny early room reflections give the dry drum voices a shared space.
        // This remains deterministic, offline and low-memory for mobile devices.
        if (input.mode == RhythmMode.DRUMS) {
            val firstDelay = (SAMPLE_RATE * 0.019).toInt()
            val secondDelay = (SAMPLE_RATE * 0.041).toInt()
            for (i in 0 until mix.size) {
                if (i >= firstDelay) mix[i] += mix[i-firstDelay] * 0.055f
                if (i >= secondDelay) mix[i] += mix[i-secondDelay] * 0.027f
            }
        }
        val volume = input.volume.coerceIn(0f, 1f)
        return ShortArray(frames) { i ->
            val x = mix[i]
            val compressed = (x / (1f + abs(x) * 0.38f) * volume).coerceIn(-1f, 1f)
            (compressed * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun addHit(mix: FloatArray, start: Int, sample: FloatArray, gain: Float) {
        for (i in sample.indices) {
            val index = (start + i) % mix.size
            mix[index] += sample[i] * gain
        }
    }

    /**
     * Acoustic-inspired rock kick: sharp felt-beater attack, membrane pitch
     * sweep, resonant shell and sub-bass air column. Three subtle variations.
     */
    private fun synthKick(variant: Int): FloatArray {
        val frames = (SAMPLE_RATE * 0.48).toInt()
        val rng = Random(1670 + variant * 13)
        var phase = 0.0
        var phaseShell = 0.0
        var lowNoise = 0.0
        return FloatArray(frames) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val pitch = 46.0 + (116 + variant * 7) * exp(-t * 30.0)
            phase += 2 * PI * pitch / SAMPLE_RATE
            phaseShell += 2 * PI * 91.0 / SAMPLE_RATE
            val membrane = sin(phase) * exp(-t * (11.0 + variant * 0.35))
            val shell = sin(phaseShell) * exp(-t * 34.0) * 0.18
            val n = rng.nextDouble(-1.0, 1.0)
            lowNoise = lowNoise * 0.7 + n * 0.3
            val beater = (n-lowNoise) * exp(-t*210.0) * 0.36
            ((membrane * 0.80 + shell + beater) *
                (1.0 - exp(-t * 4200.0))).toFloat()
        }
    }

    /**
     * Drum-shell modes + snare-wire rattle + high-frequency stick transient.
     * No single white-noise 'beep' repeated across the entire pattern.
     */
    private fun synthSnare(variant: Int): FloatArray {
        val frames = (SAMPLE_RATE * 0.36).toInt()
        val rng = Random(2091 + variant * 17)
        var previous = 0.0
        var low = 0.0
        var phase = 0.0
        var phase2 = 0.0
        return FloatArray(frames) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val noise = rng.nextDouble(-1.0, 1.0)
            low = low * 0.90 + noise * 0.10
            val high = noise - low
            val wire = high - 0.5 * previous
            previous = high
            phase += 2 * PI * (190.0 + variant * 3) / SAMPLE_RATE
            phase2 += 2 * PI * 327.0 / SAMPLE_RATE
            val body = (sin(phase) + 0.30*sin(phase2)) * exp(-t*20.0)
            val snareWire = wire * (0.75*exp(-t*15.0)+0.25*exp(-t*56.0))
            val stick = high * exp(-t*150.0)
            ((body*0.38 + snareWire*0.59 + stick*0.27) *
                (1.0-exp(-t*5000.0))).toFloat()
        }
    }

    /** Multi-band metallic spectrum with different decay times, like cymbal plates. */
    private fun synthHat(open: Boolean, variant: Int): FloatArray {
        val duration = if (open) 0.68 else 0.18
        val frames = (SAMPLE_RATE * duration).toInt()
        val rng = Random(4311 + variant*23 + if(open) 197 else 0)
        val frequencies = doubleArrayOf(3317.0, 4591.0, 5975.0, 7421.0, 9083.0)
        val phase = DoubleArray(frequencies.size)
        var filtered = 0.0
        return FloatArray(frames) { i ->
            val t = i.toDouble()/SAMPLE_RATE
            val raw = rng.nextDouble(-1.0,1.0)
            filtered = filtered*0.985 + raw*0.015
            val noise = raw - filtered
            var metal = 0.0
            for (j in frequencies.indices) {
                phase[j] += 2*PI*(frequencies[j]+variant*9.0)/SAMPLE_RATE
                metal += sin(phase[j])*0.10
            }
            val release = if(open) 7.5 else 32.0
            val envelope = exp(-t*release) *
                (1.0-exp(-t*12000.0)) *
                (0.9+0.1*cos(2*PI*36*t))
            ((noise*0.55 + metal)*envelope).toFloat()
        }
    }

    /** Large crash with a shimmering metallic body or a controlled ride ping. */
    private fun synthCymbal(ride: Boolean, variant: Int): FloatArray {
        val duration = if(ride) 0.82 else 1.25
        val frames = (SAMPLE_RATE * duration).toInt()
        val rng = Random(8703 + variant*37 + if(ride) 31 else 0)
        val frequencies = doubleArrayOf(1267.0, 1912.0, 2821.0, 4017.0, 5749.0, 8134.0)
        val phase = DoubleArray(frequencies.size)
        var low = 0.0
        return FloatArray(frames) { i ->
            val t=i.toDouble()/SAMPLE_RATE
            val noise=rng.nextDouble(-1.0,1.0)
            low=low*0.994+noise*0.006
            var metal=0.0
            for(j in frequencies.indices) {
                phase[j]+=2*PI*(frequencies[j]+variant*11.0)/SAMPLE_RATE
                metal+=sin(phase[j])*0.08
            }
            val envelope=exp(-t*(if(ride) 10.0 else 3.5)) *
                (1.0-exp(-t*(if(ride) 8000.0 else 1800.0)))
            ((metal*0.38 + (noise-low)*0.36)*envelope).toFloat()
        }
    }

    private fun synthClick(accent: Boolean): FloatArray {
        val frames = (SAMPLE_RATE * 0.063).toInt()
        val frequency = if (accent) 1320.0 else 920.0
        return FloatArray(frames) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            (sin(2 * PI * frequency * t) * exp(-t * 75)).toFloat()
        }
    }
}
