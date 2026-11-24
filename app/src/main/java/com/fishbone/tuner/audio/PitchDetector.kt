package com.fishbone.tuner.audio

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * YIN fundamental-frequency detector for guitar and bass, including A0 (27.5 Hz).
 *
 * A full 8192-sample window at 44.1 kHz contains over five cycles of A0.
 * Samples are pair-averaged to 22.05 kHz for efficient pitch analysis without
 * throwing away the low-frequency information. No raw audio leaves the device.
 */
class PitchDetector(private val sampleRate: Int = 44100) {
    companion object {
        const val WINDOW_SIZE = 8192
        const val MIN_FREQUENCY_HZ = 23.0
        const val MAX_FREQUENCY_HZ = 1050.0
        private const val CONFIDENT_THRESHOLD = 0.16
        private const val WEAK_THRESHOLD = 0.25
        private const val MIN_RMS = 0.0025
    }

    fun detect(samples: ShortArray): Double? {
        if (samples.size < WINDOW_SIZE) return null

        val offset = samples.size - WINDOW_SIZE
        val n = WINDOW_SIZE / 2
        val sr = sampleRate / 2.0

        // Two-tap lowpass decimation and DC rejection remove a common source
        // of low-frequency bias on mobile microphones.
        val signal = DoubleArray(n) { i ->
            val at = offset + i * 2
            (samples[at].toDouble() + samples[at + 1].toDouble()) / 65536.0
        }
        val mean = signal.average()
        var energy = 0.0
        for (i in signal.indices) {
            signal[i] -= mean
            energy += signal[i] * signal[i]
        }
        if (sqrt(energy / n) < MIN_RMS) return null

        val minTau = max(2, floor(sr / MAX_FREQUENCY_HZ).toInt())
        val maxTau = min(n / 2 - 1, floor(sr / MIN_FREQUENCY_HZ).toInt())
        if (maxTau <= minTau) return null

        // Equal-sized comparison windows prevent a systematic CMNDF bias as
        // lag increases. O(3M) per frame after decimation, suitable for mobile.
        val comparisons = n - maxTau
        val cmndf = DoubleArray(maxTau + 1)
        var cumulative = 0.0
        var bestTau = -1
        var bestScore = Double.POSITIVE_INFINITY
        var selectedTau = -1
        for (tau in 1..maxTau) {
            var difference = 0.0
            for (i in 0 until comparisons) {
                val delta = signal[i] - signal[i + tau]
                difference += delta * delta
            }
            cumulative += difference
            val score = if (cumulative > 1e-15) {
                difference * tau / cumulative
            } else 1.0
            cmndf[tau] = score
            if (tau < minTau) continue

            if (score < bestScore) {
                bestScore = score
                bestTau = tau
            }
            // The earliest confident minimum is usually the fundamental.
            if (score < CONFIDENT_THRESHOLD) {
                selectedTau = tau
                break
            }
        }

        var tau = if (selectedTau >= 0) selectedTau else bestTau
        if (tau < 0 || cmndf[tau] > WEAK_THRESHOLD) return null
        while (tau + 1 <= maxTau && cmndf[tau + 1] < cmndf[tau]) tau++

        val refinedLag = if (tau > 1 && tau < maxTau) {
            val left = cmndf[tau - 1]
            val mid = cmndf[tau]
            val right = cmndf[tau + 1]
            val curvature = left - 2.0 * mid + right
            if (abs(curvature) > 1e-12) {
                tau + (0.5 * (left - right) / curvature).coerceIn(-1.0, 1.0)
            } else tau.toDouble()
        } else tau.toDouble()

        val hz = sr / refinedLag
        return hz.takeIf {
            it.isFinite() && it >= MIN_FREQUENCY_HZ && it <= MAX_FREQUENCY_HZ
        }
    }
}
