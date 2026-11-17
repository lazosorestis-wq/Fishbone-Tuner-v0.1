package com.fishbone.tuner.audio

import kotlin.math.*

/** YIN pitch estimation on a mono PCM 16-bit frame. Returns null when no stable pitch exists. */
class PitchDetector(private val sampleRate: Int = 44100) {
    private val threshold = 0.15

    fun detect(samples: ShortArray): Double? {
        val n = samples.size
        if (n < 2048) return null
        var energy = 0.0
        val signal = DoubleArray(n) { i ->
            val x = samples[i] / 32768.0
            energy += x * x
            x
        }
        if (sqrt(energy / n) < 0.008) return null

        // Guitar and bass range (~28 Hz to 1000 Hz). 4096 samples resolve low bass notes.
        val minTau = max(2, sampleRate / 1000)
        val maxTau = min(n / 2 - 1, sampleRate / 28)
        val difference = DoubleArray(maxTau + 1)
        for (tau in 1..maxTau) {
            var sum = 0.0
            for (i in 0 until (n - maxTau)) {
                val delta = signal[i] - signal[i + tau]
                sum += delta * delta
            }
            difference[tau] = sum
        }
        val cmndf = DoubleArray(maxTau + 1) { 1.0 }
        var cumulative = 0.0
        for (tau in 1..maxTau) {
            cumulative += difference[tau]
            cmndf[tau] = if (cumulative <= 0.0) 1.0 else difference[tau] * tau / cumulative
        }
        var tau = minTau
        while (tau < maxTau) {
            if (cmndf[tau] < threshold) {
                while (tau + 1 <= maxTau && cmndf[tau + 1] < cmndf[tau]) tau++
                break
            }
            tau++
        }
        if (tau >= maxTau || cmndf[tau] > 0.25) return null
        val refined = if (tau > 1 && tau < maxTau) {
            val left = cmndf[tau - 1]
            val mid = cmndf[tau]
            val right = cmndf[tau + 1]
            val denom = left - 2.0 * mid + right
            if (abs(denom) > 1e-12) tau + (0.5 * (left - right) / denom).coerceIn(-1.0, 1.0) else tau.toDouble()
        } else tau.toDouble()
        return (sampleRate / refined).takeIf { it.isFinite() && it in 28.0..1000.0 }
    }
}
