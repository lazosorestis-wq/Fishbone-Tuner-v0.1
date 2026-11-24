package com.fishbone.tuner.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class PitchDetectorTest {
    private val detector = PitchDetector(44100)

    private fun tone(
        hz: Double,
        fundamental: Double = 0.45,
        second: Double = 0.0,
        third: Double = 0.0,
        noise: Double = 0.0
    ): ShortArray {
        val random = Random(1717)
        return ShortArray(PitchDetector.WINDOW_SIZE) { index ->
            val t = index / 44100.0
            val signal =
                fundamental * sin(2.0 * PI * hz * t) +
                second * sin(2.0 * PI * hz * 2.0 * t) +
                third * sin(2.0 * PI * hz * 3.0 * t) +
                noise * random.nextDouble(-1.0, 1.0)
            (signal.coerceIn(-0.99, 0.99) * 32767.0).roundToInt().toShort()
        }
    }

    private fun assertPitch(expected: Double, data: ShortArray, toleranceCents: Double = 15.0) {
        val actual = detector.detect(data)
        assertNotNull("Expected $expected Hz but detector returned no pitch", actual)
        val cents = 1200.0 * kotlin.math.log2(actual!! / expected)
        org.junit.Assert.assertTrue(
            "Expected $expected Hz, got $actual Hz (error $cents cents)",
            kotlin.math.abs(cents) < toleranceCents
        )
    }

    @Test fun detectsDropA6StringLowA1() = assertPitch(55.0, tone(55.0))
    @Test fun detectsDropA5StringBassLowA0() = assertPitch(27.5, tone(27.5), 20.0)
    @Test fun detectsStandardBassE1() = assertPitch(41.2034, tone(41.2034))
    @Test fun detectsLowEightStringFSharp() = assertPitch(46.2493, tone(46.2493))
    @Test fun detectsStandardGuitarE2() = assertPitch(82.4069, tone(82.4069))
    @Test fun detectsHighGuitarE4() = assertPitch(329.6276, tone(329.6276))
    @Test fun detectsConcertA4() = assertPitch(440.0, tone(440.0))
    @Test fun detectsHarmonicRichDropA() = assertPitch(
        55.0, tone(55.0, fundamental = 0.22, second = 0.46, third = 0.18, noise = 0.015), 20.0
    )
    @Test fun silenceIsNotMisreadAsPitch() {
        assertNull(detector.detect(ShortArray(PitchDetector.WINDOW_SIZE)))
    }
    @Test fun quietRandomNoiseIsNotMisreadAsPitch() {
        val random = Random(1234)
        val noise = ShortArray(PitchDetector.WINDOW_SIZE) {
            random.nextInt(-210, 211).toShort()
        }
        assertNull(detector.detect(noise))
    }
    @Test fun dropAPresetsAreDistinct() {
        val six = Tunings.all.single { it.title == "Guitar · Drop A (6-string)" }
        val seven = Tunings.all.single { it.title == "Guitar · Drop A (7-string)" }
        val bass = Tunings.all.single { it.title == "Bass · Drop A (5-string)" }
        assertEquals(listOf(33,40,45,50,54,59), six.strings.map { it.midi })
        assertEquals(listOf(33,40,45,50,55,59,64), seven.strings.map { it.midi })
        assertEquals(listOf(21,28,33,38,43), bass.strings.map { it.midi })
        assertEquals(27.5, Tunings.freq(21), 0.02)
        assertEquals(55.0, Tunings.freq(33), 0.02)
    }
}
