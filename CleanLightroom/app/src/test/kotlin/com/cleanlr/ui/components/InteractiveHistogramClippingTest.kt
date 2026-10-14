package com.cleanlr.ui.components

import androidx.compose.ui.graphics.Color
import com.cleanlr.engine.HistogramData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveHistogramClippingTest {

    @Test
    fun `test Adobe Spectrum clipping indicator colors match exact normative tokens`() {
        // Shadow clipping: #0055FF (PDF Page 3)
        assertEquals(Color(0xFF0055FF), ClippingIndicatorMath.SHADOW_CLIPPING_COLOR)

        // Highlight clipping: #FF2222 (PDF Page 3)
        assertEquals(Color(0xFFFF2222), ClippingIndicatorMath.HIGHLIGHT_CLIPPING_COLOR)

        // Inactive surface: #2A2A2A (spectrum_surface_elevated)
        assertEquals(Color(0xFF2A2A2A), ClippingIndicatorMath.INACTIVE_COLOR)

        // Inactive border: #656565 (spectrum_text_disabled)
        assertEquals(Color(0xFF656565), ClippingIndicatorMath.INACTIVE_BORDER_COLOR)
    }

    @Test
    fun `test shadow clipping is detected when luma or RGB bin 0 is greater than zero`() {
        val emptyData = HistogramData()
        assertFalse(ClippingIndicatorMath.isShadowClipped(emptyData))

        // Luma crushed
        val lumaCrushed = HistogramData()
        lumaCrushed.lumaBins[0] = 42
        assertTrue(ClippingIndicatorMath.isShadowClipped(lumaCrushed))

        // Red crushed
        val redCrushed = HistogramData()
        redCrushed.redBins[0] = 10
        assertTrue(ClippingIndicatorMath.isShadowClipped(redCrushed))

        // Green crushed
        val greenCrushed = HistogramData()
        greenCrushed.greenBins[0] = 15
        assertTrue(ClippingIndicatorMath.isShadowClipped(greenCrushed))

        // Blue crushed
        val blueCrushed = HistogramData()
        blueCrushed.blueBins[0] = 99
        assertTrue(ClippingIndicatorMath.isShadowClipped(blueCrushed))

        // Forced active via preview toggle
        assertTrue(ClippingIndicatorMath.isShadowClipped(emptyData, isForcedActive = true))
    }

    @Test
    fun `test highlight clipping is detected when luma or RGB bin 255 is greater than zero`() {
        val emptyData = HistogramData()
        assertFalse(ClippingIndicatorMath.isHighlightClipped(emptyData))

        // Luma blown
        val lumaBlown = HistogramData()
        lumaBlown.lumaBins[255] = 120
        assertTrue(ClippingIndicatorMath.isHighlightClipped(lumaBlown))

        // Red blown
        val redBlown = HistogramData()
        redBlown.redBins[255] = 5
        assertTrue(ClippingIndicatorMath.isHighlightClipped(redBlown))

        // Green blown
        val greenBlown = HistogramData()
        greenBlown.greenBins[255] = 88
        assertTrue(ClippingIndicatorMath.isHighlightClipped(greenBlown))

        // Blue blown
        val blueBlown = HistogramData()
        blueBlown.blueBins[255] = 7
        assertTrue(ClippingIndicatorMath.isHighlightClipped(blueBlown))

        // Forced active via preview toggle
        assertTrue(ClippingIndicatorMath.isHighlightClipped(emptyData, isForcedActive = true))
    }

    @Test
    fun `test normative 5-zone histogram boundary mapping`() {
        assertEquals(HistogramZone.BLACKS, HistogramZone.fromBin(0))
        assertEquals(HistogramZone.BLACKS, HistogramZone.fromBin(25))

        assertEquals(HistogramZone.SHADOWS, HistogramZone.fromBin(26))
        assertEquals(HistogramZone.SHADOWS, HistogramZone.fromBin(75))

        assertEquals(HistogramZone.EXPOSURE, HistogramZone.fromBin(76))
        assertEquals(HistogramZone.EXPOSURE, HistogramZone.fromBin(180))

        assertEquals(HistogramZone.HIGHLIGHTS, HistogramZone.fromBin(181))
        assertEquals(HistogramZone.HIGHLIGHTS, HistogramZone.fromBin(230))

        assertEquals(HistogramZone.WHITES, HistogramZone.fromBin(231))
        assertEquals(HistogramZone.WHITES, HistogramZone.fromBin(255))
    }
}
