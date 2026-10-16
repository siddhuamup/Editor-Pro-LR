package com.cleanlr.model

import kotlinx.serialization.Serializable

/**
 * CleanLightroom EditRecipe Data Model
 * Compliant with Adobe Camera Raw Process Version 2012 (PV2012) and Document 5 specifications.
 */
@Serializable
data class EditRecipe(
    val version: Int = 1,
    val assetId: String = "sample_image",
    val updatedAt: Long = System.currentTimeMillis(),
    val isHdrEnabled: Boolean = false,
    val basic: BasicAdjustments = BasicAdjustments(),
    val effects: EffectsAdjustments = EffectsAdjustments(),
    val detail: DetailAdjustments = DetailAdjustments(),
    val curves: ToneCurves = ToneCurves.default(),
    val hsl: HslAdjustments = HslAdjustments.default(),
    val colorGrading: ColorGrading = ColorGrading.default(),
    val masks: List<MaskLayer> = emptyList(),
    // Backward compatibility for legacy stage access
    val presence: PresenceAdjustments = PresenceAdjustments(
        texture = effects.texture,
        clarity = effects.clarity,
        dehaze = effects.dehaze,
        vibrance = basic.vibrance,
        saturation = basic.saturation
    )
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
    val tint: Float = 0.0f,             // -150f to +150f
    val vibrance: Float = 0.0f,         // -100f to +100f (Document 5)
    val saturation: Float = 0.0f        // -100f to +100f (Document 5)
)

@Serializable
data class EffectsAdjustments(
    val texture: Float = 0.0f,          // -100f to +100f
    val clarity: Float = 0.0f,          // -100f to +100f (Local Laplacian)
    val dehaze: Float = 0.0f,           // -100f to +100f
    val vignetteAmount: Float = 0.0f,   // -100f to +100f
    val vignetteMidpoint: Float = 50.0f,// 0f to 100f (50 default)
    val vignetteFeather: Float = 50.0f, // 0f to 100f (50 default)
    val grainAmount: Float = 0.0f,      // 0f to 100f
    val grainSize: Float = 25.0f,       // 0f to 100f (25 default)
    val grainRoughness: Float = 50.0f   // 0f to 100f (50 default)
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
data class DetailAdjustments(
    val sharpening: Float = 0.0f,       // 0f to 150f (neutral 0.0f, Raw default 40.0f)
    val sharpenRadius: Float = 1.0f,    // 0.5f to 3.0f (1.0 default)
    val sharpenDetail: Float = 25.0f,   // 0f to 100f (25 default)
    val sharpenMasking: Float = 0.0f,   // 0f to 100f (0 default)
    val luminanceNoise: Float = 0.0f,   // 0f to 100f (0 default)
    val colorNoise: Float = 25.0f,      // 0f to 100f (25 default)
    // Backward compatibility aliases
    val masking: Float = sharpenMasking,
    val noiseReduction: Float = luminanceNoise,
    val vignetteAmount: Float = 0.0f,
    val vignetteMidpoint: Float = 50.0f,
    val vignetteFeather: Float = 50.0f,
    val grainAmount: Float = 0.0f,
    val grainSize: Float = 25.0f,
    val grainRoughness: Float = 50.0f
)

@Serializable
data class CurvePoint(val x: Float, val y: Float)

@Serializable
data class ToneCurves(
    val composite: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(255f, 255f)),
    val red: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(255f, 255f)),
    val green: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(255f, 255f)),
    val blue: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(255f, 255f)),
    // Parametric splits & pointCurve alias for pipeline compatibility
    val parametricHighlights: Float = 0.0f,
    val parametricLights: Float = 0.0f,
    val parametricDarks: Float = 0.0f,
    val parametricShadows: Float = 0.0f,
    val splitShadow: Float = 64.0f,
    val splitMid: Float = 128.0f,
    val splitHighlight: Float = 192.0f,
    val pointCurve: List<CurvePoint> = composite
) {
    companion object {
        fun default() = ToneCurves()
    }
}

@Serializable
data class HslValues(
    val hue: Float = 0.0f,        // -100 to +100
    val saturation: Float = 0.0f, // -100 to +100
    val luminance: Float = 0.0f   // -100 to +100
)

typealias HslChannel = HslValues

@Serializable
data class HslAdjustments(
    val red: HslValues = HslValues(),
    val orange: HslValues = HslValues(),
    val yellow: HslValues = HslValues(),
    val green: HslValues = HslValues(),
    val aqua: HslValues = HslValues(),
    val blue: HslValues = HslValues(),
    val purple: HslValues = HslValues(),
    val magenta: HslValues = HslValues()
) {
    companion object {
        fun default() = HslAdjustments()
    }
}

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
    val global: ColorGradingWheel = ColorGradingWheel(),
    val blending: Float = 50.0f,  // 0 to 100 (50 default)
    val balance: Float = 0.0f     // -100 to +100 (0 default)
) {
    companion object {
        fun default() = ColorGrading()
    }
}

@Serializable
data class MaskLayer(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = true,
    val opacity: Float = 1.0f,
    val subMasks: List<SubMask> = emptyList(),
    val localAdjustments: BasicAdjustments = BasicAdjustments()
)

@Serializable
sealed class SubMask {
    abstract val id: String
    abstract val isInverted: Boolean

    @Serializable
    data class Brush(
        override val id: String,
        override val isInverted: Boolean = false,
        val points: List<BrushStrokePoint> = emptyList()
    ) : SubMask()

    @Serializable
    data class LinearGradient(
        override val id: String,
        override val isInverted: Boolean = false,
        val startX: Float = 0f,
        val startY: Float = 0f,
        val endX: Float = 0f,
        val endY: Float = 0f
    ) : SubMask()

    @Serializable
    data class AISubject(
        override val id: String,
        override val isInverted: Boolean = false,
        val maskAssetPath: String? = null
    ) : SubMask()
}

@Serializable
data class BrushStrokePoint(
    val x: Float,
    val y: Float,
    val radius: Float,
    val feather: Float,
    val flow: Float
)
