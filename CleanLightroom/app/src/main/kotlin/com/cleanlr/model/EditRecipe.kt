package com.cleanlr.model

import kotlinx.serialization.Serializable

@Serializable
data class EditRecipe(
    val assetId: String = "sample_image",
    val updatedAt: Long = System.currentTimeMillis(),
    val basic: BasicAdjustments = BasicAdjustments(),
    val presence: PresenceAdjustments = PresenceAdjustments(),
    val curves: ToneCurves = ToneCurves(),
    val hsl: HslAdjustments = HslAdjustments(),
    val colorGrading: ColorGrading = ColorGrading(),
    val detail: DetailAdjustments = DetailAdjustments()
)

@Serializable
data class BasicAdjustments(
    val exposure: Float = 0.0f,         // -5.0f to +5.0f EV
    val contrast: Float = 0.0f,         // -100f to +100f
    val highlights: Float = 0.0f,       // -100f to +100f
    val shadows: Float = 0.0f,          // -100f to +100f
    val whites: Float = 0.0f,           // -100f to +100f
    val blacks: Float = 0.0f,           // -100f to +100f
    val temperature: Float = 5500.0f,   // 2000f to 50000f Kelvin
    val tint: Float = 0.0f              // -150f to +150f
)

@Serializable
data class PresenceAdjustments(
    val texture: Float = 0.0f,          // -100f to +100f
    val clarity: Float = 0.0f,          // -100f to +100f
    val dehaze: Float = 0.0f,           // -100f to +100f
    val vibrance: Float = 0.0f,         // -100f to +100f
    val saturation: Float = 0.0f        // -100f to +100f
)

@Serializable
data class CurvePoint(val x: Float, val y: Float)

@Serializable
data class ToneCurves(
    val parametricHighlights: Float = 0.0f,
    val parametricLights: Float = 0.0f,
    val parametricDarks: Float = 0.0f,
    val parametricShadows: Float = 0.0f,
    val splitShadow: Float = 64.0f,
    val splitMid: Float = 128.0f,
    val splitHighlight: Float = 192.0f,
    val pointCurve: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(255f, 255f))
)

@Serializable
data class HslChannel(
    val hue: Float = 0.0f,        // -100 to +100
    val saturation: Float = 0.0f, // -100 to +100
    val luminance: Float = 0.0f   // -100 to +100
)

@Serializable
data class HslAdjustments(
    val red: HslChannel = HslChannel(),
    val orange: HslChannel = HslChannel(),
    val yellow: HslChannel = HslChannel(),
    val green: HslChannel = HslChannel(),
    val aqua: HslChannel = HslChannel(),
    val blue: HslChannel = HslChannel(),
    val purple: HslChannel = HslChannel(),
    val magenta: HslChannel = HslChannel()
)

@Serializable
data class ColorGradingWheel(
    val hue: Float = 0.0f,        // 0 to 360 degrees
    val saturation: Float = 0.0f, // 0 to 100
    val luminance: Float = 0.0f   // -100 to +100
)

@Serializable
data class ColorGrading(
    val shadows: ColorGradingWheel = ColorGradingWheel(),
    val midtones: ColorGradingWheel = ColorGradingWheel(),
    val highlights: ColorGradingWheel = ColorGradingWheel(),
    val blending: Float = 50.0f,  // 0 to 100
    val balance: Float = 0.0f     // -100 to +100
)

@Serializable
data class DetailAdjustments(
    val sharpening: Float = 40.0f,
    val masking: Float = 0.0f,
    val noiseReduction: Float = 0.0f
)
