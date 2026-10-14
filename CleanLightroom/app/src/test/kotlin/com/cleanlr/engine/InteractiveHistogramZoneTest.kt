package com.cleanlr.engine

import com.cleanlr.ui.components.HistogramZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InteractiveHistogramZoneTest {

    @Test
    fun testAll256BinsStrictPartition() {
        // Section 3.1: 5 Zones mapping
        // Blacks: 0..25, Shadows: 26..75, Exposure: 76..180, Highlights: 181..230, Whites: 231..255
        var blacksCount = 0
        var shadowsCount = 0
        var exposureCount = 0
        var highlightsCount = 0
        var whitesCount = 0

        for (bin in 0..255) {
            when (HistogramZone.fromBin(bin)) {
                HistogramZone.BLACKS -> blacksCount++
                HistogramZone.SHADOWS -> shadowsCount++
                HistogramZone.EXPOSURE -> exposureCount++
                HistogramZone.HIGHLIGHTS -> highlightsCount++
                HistogramZone.WHITES -> whitesCount++
                HistogramZone.NONE -> {}
            }
        }

        assertEquals("Blacks zone must cover exactly 26 bins (0..25)", 26, blacksCount)
        assertEquals("Shadows zone must cover exactly 50 bins (26..75)", 50, shadowsCount)
        assertEquals("Exposure zone must cover exactly 105 bins (76..180)", 105, exposureCount)
        assertEquals("Highlights zone must cover exactly 50 bins (181..230)", 50, highlightsCount)
        assertEquals("Whites zone must cover exactly 25 bins (231..255)", 25, whitesCount)

        // Total bins must be 256
        assertEquals(256, blacksCount + shadowsCount + exposureCount + highlightsCount + whitesCount)
    }

    @Test
    fun testExactBoundaryTransitions() {
        // Boundary 1: Blacks / Shadows (Bin 25 -> 26)
        assertEquals(HistogramZone.BLACKS, HistogramZone.fromBin(25))
        assertEquals(HistogramZone.SHADOWS, HistogramZone.fromBin(26))

        // Boundary 2: Shadows / Exposure (Bin 75 -> 76)
        assertEquals(HistogramZone.SHADOWS, HistogramZone.fromBin(75))
        assertEquals(HistogramZone.EXPOSURE, HistogramZone.fromBin(76))

        // Boundary 3: Exposure / Highlights (Bin 180 -> 181)
        assertEquals(HistogramZone.EXPOSURE, HistogramZone.fromBin(180))
        assertEquals(HistogramZone.HIGHLIGHTS, HistogramZone.fromBin(181))

        // Boundary 4: Highlights / Whites (Bin 230 -> 231)
        assertEquals(HistogramZone.HIGHLIGHTS, HistogramZone.fromBin(230))
        assertEquals(HistogramZone.WHITES, HistogramZone.fromBin(231))
    }

    @Test
    fun testNormalizedRatioResolution() {
        // Leftmost endpoint (0.0) -> BLACKS
        assertEquals(HistogramZone.BLACKS, HistogramZone.fromRatio(0.0f))

        // Center mid-gray (~0.50) -> EXPOSURE
        assertEquals(HistogramZone.EXPOSURE, HistogramZone.fromRatio(0.50f))

        // Rightmost endpoint (1.0) -> WHITES
        assertEquals(HistogramZone.WHITES, HistogramZone.fromRatio(1.0f))

        // Clamping check
        assertEquals(HistogramZone.BLACKS, HistogramZone.fromRatio(-0.2f))
        assertEquals(HistogramZone.WHITES, HistogramZone.fromRatio(1.5f))
    }
}
