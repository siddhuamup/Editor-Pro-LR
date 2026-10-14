package com.cleanlr.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task T4 Verification: Adobe Spectrum Dark Theme Tokens (PDF Pages 2 & 3)
 * Exact HEX and ARGB verification gate.
 */
class SpectrumThemeTokenTest {

    private fun Color.toHexRgb(): String {
        val argb = this.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format("#%02X%02X%02X", r, g, b)
    }

    private fun Color.toHexArgb(): String {
        val argb = this.toArgb()
        val a = (argb shr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format("#%02X%02X%02X%02X", a, r, g, b)
    }

    @Test
    fun testAdobeSpectrumDarkNormativeHexTokens() {
        // 1. Canvas Background: loupe_background = #121212
        assertEquals("#121212", loupe_background.toHexRgb())
        assertEquals("#121212", SpectrumCanvas.toHexRgb())

        // 2. Surface (Panels): spectrum_surface_dark = #1E1E1E
        assertEquals("#1E1E1E", spectrum_surface_dark.toHexRgb())
        assertEquals("#1E1E1E", SpectrumSurface.toHexRgb())

        // 3. Surface Elevated: spectrum_surface_elevated = #2A2A2A
        assertEquals("#2A2A2A", spectrum_surface_elevated.toHexRgb())
        assertEquals("#2A2A2A", SpectrumElevated.toHexRgb())

        // 4. Border / Divider: loupe_bottombar_border = #383838
        assertEquals("#383838", loupe_bottombar_border.toHexRgb())
        assertEquals("#383838", SpectrumBorder.toHexRgb())

        // 5. Accent Primary: spectrum_400_blue = #1473E6
        assertEquals("#1473E6", spectrum_400_blue.toHexRgb())
        assertEquals("#1473E6", SpectrumBlue.toHexRgb())

        // 6. Accent Hover / Active: spectrum_500_blue = #2680EB
        assertEquals("#2680EB", spectrum_500_blue.toHexRgb())
        assertEquals("#2680EB", SpectrumBlueHover.toHexRgb())

        // 7. Text Primary: spectrum_text_primary = #FFFFFF
        assertEquals("#FFFFFF", spectrum_text_primary.toHexRgb())
        assertEquals("#FFFFFF", SpectrumTextPrimary.toHexRgb())

        // 8. Text Secondary: spectrum_text_secondary = #A2A2A2
        assertEquals("#A2A2A2", spectrum_text_secondary.toHexRgb())
        assertEquals("#A2A2A2", SpectrumTextSecondary.toHexRgb())

        // 9. Text Disabled: spectrum_text_disabled = #656565
        assertEquals("#656565", spectrum_text_disabled.toHexRgb())
        assertEquals("#656565", SpectrumTextDisabled.toHexRgb())

        // 10. Mask Overlay: spectrum_mask_overlay = #80E02B2B (50% alpha Ruby Red)
        assertEquals("#80E02B2B", spectrum_mask_overlay.toHexArgb())
        assertEquals("#80E02B2B", SpectrumMaskOverlay.toHexArgb())

        // 11. Shadow Clipping: spectrum_clipping_shadow = #0055FF
        assertEquals("#0055FF", spectrum_clipping_shadow.toHexRgb())
        assertEquals("#0055FF", ClipBlackIndicator.toHexRgb())

        // 12. Highlight Clipping: spectrum_clipping_high = #FF2222
        assertEquals("#FF2222", spectrum_clipping_high.toHexRgb())
        assertEquals("#FF2222", ClipWhiteIndicator.toHexRgb())
    }
}
