package com.cleanlr.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Adobe Spectrum Dark Theme Tokens (Normative Spec: PDF Part A Section 1)
// =============================================================================

// Canvas Background (loupe_background): #121212 (फोटोच्या मागे असणारा मुख्य कॅनव्हास)
val loupe_background = Color(0xFF121212)
val SpectrumCanvas = loupe_background

// Surface Panels (spectrum_surface_dark): #1E1E1E (बॉटम बार आणि अॅडजस्टमेंट पॅनेल्स)
val spectrum_surface_dark = Color(0xFF1E1E1E)
val SpectrumSurface = spectrum_surface_dark

// Surface Elevated (spectrum_surface_elevated): #2A2A2A (फ्लोटिंग टूल्स, कार्ड्स आणि डायलॉग्स)
val spectrum_surface_elevated = Color(0xFF2A2A2A)
val SpectrumElevated = spectrum_surface_elevated

// Border / Divider (loupe_bottombar_border): #383838 (टूल्स आणि पॅनेल विभाजक रेषा)
val loupe_bottombar_border = Color(0xFF383838)
val SpectrumBorder = loupe_bottombar_border

// Primary Accent (spectrum_400_blue): #1473E6 (अॅक्टिव्ह स्लाइडर ट्रॅक, सेव्ह बटण, चेकबॉक्स)
val spectrum_400_blue = Color(0xFF1473E6)
val SpectrumBlue = spectrum_400_blue

// Hover / Active Accent (spectrum_500_blue): #2680EB (प्रेस्ड / हायलाइटेड स्टेट)
val spectrum_500_blue = Color(0xFF2680EB)
val SpectrumBlueHover = spectrum_500_blue

// Text Primary (spectrum_text_primary): #FFFFFF (मुख्य लेबल्स, स्लाइडर व्हॅल्यूज)
val spectrum_text_primary = Color(0xFFFFFFFF)
val SpectrumTextPrimary = spectrum_text_primary

// Text Secondary (spectrum_text_secondary): #A2A2A2 (दुय्यम लेबल्स, युनिट्स, अनसिलेक्टेड आयकॉन्स)
val spectrum_text_secondary = Color(0xFFA2A2A2)
val SpectrumTextSecondary = spectrum_text_secondary

// Text Disabled (spectrum_text_disabled): #656565 (डिसेबल झालेली बटणे)
val spectrum_text_disabled = Color(0xFF656565)
val SpectrumTextDisabled = spectrum_text_disabled

// Mask Overlay (spectrum_mask_overlay): #80E02B2B (मास्क केलेला भाग दाखवण्यासाठी ५०% पारदर्शक लाल)
val spectrum_mask_overlay = Color(0x80E02B2B)
val SpectrumMaskOverlay = spectrum_mask_overlay

// Clipping Indicators (PDF Page 3)
// Shadow Clipping (spectrum_clipping_shadow): #0055FF (सावली क्लिप / Under-exposed झाल्यावर दिसणारा निळा रंग)
val spectrum_clipping_shadow = Color(0xFF0055FF)
val ClipBlackIndicator = spectrum_clipping_shadow

// Highlight Clipping (spectrum_clipping_high): #FF2222 (हायलाइट बर्न / Over-exposed झाल्यावर दिसणारा लाल रंग)
val spectrum_clipping_high = Color(0xFFFF2222)
val ClipWhiteIndicator = spectrum_clipping_high

// Histogram Colors
val HistogramRed = Color(0xFFE24A4A)
val HistogramGreen = Color(0xFF4AE26B)
val HistogramBlue = Color(0xFF4A90E2)
val HistogramLuma = Color(0x88CCCCCC)

// =============================================================================
// Pro Darkroom Ergonomics & Surface Tokens (Tips 1–35)
// =============================================================================

// Tip 1: Canvas Background Options
val CanvasPureBlack = Color(0xFF000000)
val CanvasDarkGray = Color(0xFF121212)
val CanvasMiddleGray = Color(0xFF808080)
val CanvasWhite = Color(0xFFFFFFFF)

enum class CanvasBackground(val label: String, val color: Color) {
    DARK_GRAY("Dark Gray", CanvasDarkGray),
    PURE_BLACK("Pure Black", CanvasPureBlack),
    MIDDLE_GRAY("Middle Gray", CanvasMiddleGray),
    WHITE("White", CanvasWhite)
}

// Tip 4: 1.dp subtle inner border so night photos don't bleed into canvas
val SpectrumInnerBorder = Color(0xFF262626)

// Tip 7: Pill Drag Handle
val SpectrumPillHandle = Color(0xFF484848)

// Tip 12: OLED anti-burn-in icon color (92% white)
val SpectrumIconNormal = Color(0xFFEBEBEB)

// Tip 14: Solid disabled token (prevents transparency layering artifacts)
val SpectrumIconDisabled = Color(0xFF4A4A4A)

// Tip 15 & 16: Panel Cards & Elevated Dialog rims
val SpectrumCardBorder = Color(0xFF2E2E2E)
val SpectrumTopHighlight = Color(0xFF3E3E3E)

// Tip 17: SDR Nit Lock white (100-200 nits equivalent) during HDR editing
val SdrNitLockedWhite = Color(0xFFB0B0B0)

// Tip 23: Active modification indicator dot
val SpectrumActiveDot = Color(0xFF1473E6)

// Tip 29 & 30: Typography & Value states
val SpectrumSliderLabel = Color(0xFFB0B0B0)
val SpectrumSliderDefault = Color(0xFF8E8E8E)
val SpectrumSliderModified = Color(0xFF38A9FF)

// Tip 31 & 32: Group Header & Divider
val SpectrumGroupHeader = Color(0xFF7A7A7A)
val SpectrumGroupDivider = Color(0xFF2A2A2A)

// Tip 35: Floating Pill Dark Tooltips & Toasts
val SpectrumToastSurface = Color(0xFF2C2C2C)
val SpectrumToastBorder = Color(0xFF3E3E3E)

// Tip 6: 3-Snap Bottom Sheet State
enum class PanelSnapState(val heightDp: Int) {
    COLLAPSED(0),
    COMPACT(210),
    EXPANDED(380)
}

