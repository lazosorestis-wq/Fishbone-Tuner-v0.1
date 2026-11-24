package com.fishbone.tuner.audio

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

data class StringNote(val label: String, val midi: Int)
data class Tuning(val title: String, val strings: List<StringNote>)

object Tunings {
    private fun notes(vararg names: Pair<String, Int>) = names.map { StringNote(it.first, it.second) }
    /**
     * String order is LOW to HIGH. Keep six- and seven-string Drop A separate;
     * their upper strings differ and are not interchangeable.
     */
    val all = listOf(
        Tuning("Guitar · Standard (E)", notes(
            "E2" to 40, "A2" to 45, "D3" to 50, "G3" to 55, "B3" to 59, "E4" to 64)),
        Tuning("Guitar · D Standard", notes(
            "D2" to 38, "G2" to 43, "C3" to 48, "F3" to 53, "A3" to 57, "D4" to 62)),
        Tuning("Guitar · C Standard", notes(
            "C2" to 36, "F2" to 41, "A#2" to 46, "D#3" to 51, "G3" to 55, "C4" to 60)),
        Tuning("Guitar · B Standard", notes(
            "B1" to 35, "E2" to 40, "A2" to 45, "D3" to 50, "F#3" to 54, "B3" to 59)),
        Tuning("Guitar · A Standard", notes(
            "A1" to 33, "D2" to 38, "G2" to 43, "C3" to 48, "E3" to 52, "A3" to 57)),
        Tuning("Guitar · Drop D", notes(
            "D2" to 38, "A2" to 45, "D3" to 50, "G3" to 55, "B3" to 59, "E4" to 64)),
        Tuning("Guitar · Drop C#", notes(
            "C#2" to 37, "G#2" to 44, "C#3" to 49, "F#3" to 54, "A#3" to 58, "D#4" to 63)),
        Tuning("Guitar · Drop C", notes(
            "C2" to 36, "G2" to 43, "C3" to 48, "F3" to 53, "A3" to 57, "D4" to 62)),
        Tuning("Guitar · Drop B", notes(
            "B1" to 35, "F#2" to 42, "B2" to 47, "E3" to 52, "G#3" to 56, "C#4" to 61)),
        Tuning("Guitar · Drop A# / Bb", notes(
            "A#1" to 34, "F2" to 41, "A#2" to 46, "D#3" to 51, "G3" to 55, "C4" to 60)),
        Tuning("Guitar · Drop A (6-string)", notes(
            "A1" to 33, "E2" to 40, "A2" to 45, "D3" to 50, "F#3" to 54, "B3" to 59)),
        Tuning("Guitar · 7-string Standard", notes(
            "B1" to 35, "E2" to 40, "A2" to 45, "D3" to 50,
            "G3" to 55, "B3" to 59, "E4" to 64)),
        Tuning("Guitar · Drop A (7-string)", notes(
            "A1" to 33, "E2" to 40, "A2" to 45, "D3" to 50,
            "G3" to 55, "B3" to 59, "E4" to 64)),
        Tuning("Guitar · 7-string A Standard", notes(
            "A1" to 33, "D2" to 38, "G2" to 43, "C3" to 48,
            "F3" to 53, "A3" to 57, "D4" to 62)),
        Tuning("Guitar · 8-string F# Standard", notes(
            "F#1" to 30, "B1" to 35, "E2" to 40, "A2" to 45,
            "D3" to 50, "G3" to 55, "B3" to 59, "E4" to 64)),
        Tuning("Bass · Standard (4-string)", notes(
            "E1" to 28, "A1" to 33, "D2" to 38, "G2" to 43)),
        Tuning("Bass · Drop D (4-string)", notes(
            "D1" to 26, "A1" to 33, "D2" to 38, "G2" to 43)),
        Tuning("Bass · 5-string Standard", notes(
            "B0" to 23, "E1" to 28, "A1" to 33, "D2" to 38, "G2" to 43)),
        Tuning("Bass · Drop A (5-string)", notes(
            "A0" to 21, "E1" to 28, "A1" to 33, "D2" to 38, "G2" to 43))
    )

    fun freq(midi: Int, referenceHz: Double = 440.0) = referenceHz * 2.0.pow((midi - 69) / 12.0)
    fun nearestMidi(hz: Double, referenceHz: Double = 440.0): Int =
        (69 + 12 * log2(hz / referenceHz)).roundToInt()
    fun name(midi: Int): String {
        val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return names[Math.floorMod(midi, 12)] + (Math.floorDiv(midi, 12) - 1)
    }
    fun cents(hz: Double, midi: Int, referenceHz: Double = 440.0): Double =
        1200 * log2(hz / freq(midi, referenceHz))
}
