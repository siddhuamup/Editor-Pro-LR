package com.cleanlr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Stage2WhiteBalanceTest {

    private val skipEpsilon = 0.001f

    private fun shouldSkip(gains: FloatArray): Boolean {
        if (gains.size < 3) return true
        return abs(gains[0] - 1.0f) < skipEpsilon &&
               abs(gains[1] - 1.0f) < skipEpsilon &&
               abs(gains[2] - 1.0f) < skipEpsilon
    }

    @Test
    fun testDaylightIdentityGainsAndSkipRule() {
        // At 5500K reference daylight and 0 tint, gains must be identity
        val gains = ColorScience.kelvinTintToRgbGain(5500f, 0f)

        assertEquals("Red gain at daylight should be 1.0", 1.0f, gains[0], 0.01f)
        assertEquals("Green gain at daylight should be 1.0", 1.0f, gains[1], 0.01f)
        assertEquals("Blue gain at daylight should be 1.0", 1.0f, gains[2], 0.01f)

        // Verifies Skip-disabled-stages rule for 120 FPS
        assertTrue("Stage 2 should be skipped at default 5500K daylight", shouldSkip(gains))
    }

    @Test
    fun testWarmTemperatureIncreasesRedGain() {
        // High kelvin (e.g. 8000K, overcast/shade) requires warming (increasing red relative to blue)
        val gains = ColorScience.kelvinTintToRgbGain(8000f, 0f)

        assertFalse("Active temperature adjustment must NOT be skipped", shouldSkip(gains))
        assertTrue("Warming should boost red gain relative to blue gain", gains[0] > gains[2])
    }

    @Test
    fun testCoolTemperatureIncreasesBlueGain() {
        // Low kelvin (e.g. 3000K, tungsten) requires cooling (increasing blue relative to red)
        val gains = ColorScience.kelvinTintToRgbGain(3000f, 0f)

        assertFalse("Active temperature adjustment must NOT be skipped", shouldSkip(gains))
        assertTrue("Cooling should boost blue gain relative to red gain", gains[2] > gains[0])
    }

    @Test
    fun testTintAdjustments() {
        // Magenta tint (+75) boosts R and B relative to G
        val magentaGains = ColorScience.kelvinTintToRgbGain(5500f, 75f)
        assertTrue("Magenta tint should increase red over green", magentaGains[0] > magentaGains[1])
        assertTrue("Magenta tint should increase blue over green", magentaGains[2] > magentaGains[1])

        // Green tint (-75) boosts G relative to R and B
        val greenGains = ColorScience.kelvinTintToRgbGain(5500f, -75f)
        assertTrue("Green tint should decrease red relative to green", greenGains[0] < greenGains[1])
        assertTrue("Green tint should decrease blue relative to green", greenGains[2] < greenGains[1])
    }
}
