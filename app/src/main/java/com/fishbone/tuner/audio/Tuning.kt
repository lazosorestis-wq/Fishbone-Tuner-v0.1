package com.fishbone.tuner.audio

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

data class StringNote(val label: String, val midi: Int)
data class Tuning(val title: String, val strings: List<StringNote>)

object Tunings {
    private fun notes(vararg names: Pair<String, Int>) = names.map { StringNote(it.first, it.second) }
    val all = listOf(
        Tuning("Guitar · Standard", notes("E2" to 40, "A2" to 45, "D3" to 50, "G3" to 55, "B3" to 59, "E4" to 64)),
        Tuning("Guitar · Drop D", notes("D2" to 38, "A2" to 45, "D3" to 50, "G3" to 55, "B3" to 59, "E4" to 64)),
        Tuning("Guitar · Drop C", notes("C2" to 36, "G2" to 43, "C3" to 48, "F3" to 53, "A3" to 57, "D4" to 62)),
        Tuning("Guitar · Drop B", notes("B1" to 35, "F#2" to 42, "B2" to 47, "E3" to 52, "G#3" to 56, "C#4" to 61)),
        Tuning("Guitar · B Standard", notes("B1" to 35, "E2" to 40, "A2" to 45, "D3" to 50, "F#3" to 54, "B3" to 59)),
        Tuning("Bass · Standard", notes("E1" to 28, "A1" to 33, "D2" to 38, "G2" to 43)),
        Tuning("Bass · Drop D", notes("D1" to 26, "A1" to 33, "D2" to 38, "G2" to 43)),
        Tuning("Bass · 5-string", notes("B0" to 23, "E1" to 28, "A1" to 33, "D2" to 38, "G2" to 43))
    )
    fun freq(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)
    fun nearestMidi(hz: Double): Int = (69 + 12 * log2(hz / 440.0)).roundToInt()
    fun name(midi: Int): String {
        val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return names[Math.floorMod(midi, 12)] + (Math.floorDiv(midi, 12) - 1)
    }
    fun cents(hz: Double, midi: Int): Double = 1200 * log2(hz / freq(midi))
}
