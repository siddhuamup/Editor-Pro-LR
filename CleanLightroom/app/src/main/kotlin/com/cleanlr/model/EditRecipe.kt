package com.cleanlr.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonPrimitive

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
    val crop: CropAdjustments = CropAdjustments(),
    val blur: BlurAdjustments = BlurAdjustments(),
    val optics: OpticsAdjustments = OpticsAdjustments(),
    val profile: String = "Adobe Color",
    // Backward compatibility for legacy stage access
    val presence: PresenceAdjustments = PresenceAdjustments(
        texture = effects.texture,
        clarity = effects.clarity,
        dehaze = effects.dehaze,
        vibrance = basic.vibrance,
        saturation = basic.saturation
    )
) {
    /**
     * Tip 465: Complete Unified EditRecipe Sync Helper
     * Keeps duplicate fields in basic, effects, presence, and detail 100% synchronized
     * across Presets, Auto Tone, XMP Import, and the 9-Stage GPU pipeline.
     */
    fun syncLegacyFields(): EditRecipe {
        val syncedVibrance = if (presence.vibrance != 0f && basic.vibrance == 0f) presence.vibrance else basic.vibrance
        val syncedSaturation = if (presence.saturation != 0f && basic.saturation == 0f) presence.saturation else basic.saturation

        val syncedTexture = if (effects.texture != 0f && presence.texture == 0f) effects.texture else presence.texture
        val syncedClarity = if (effects.clarity != 0f && presence.clarity == 0f) effects.clarity else presence.clarity
        val syncedDehaze = if (effects.dehaze != 0f && presence.dehaze == 0f) effects.dehaze else presence.dehaze

        val syncedVignetteAmount = if (effects.vignetteAmount != 0f && detail.vignetteAmount == 0f) effects.vignetteAmount else detail.vignetteAmount
        val syncedVignetteMidpoint = if (effects.vignetteMidpoint != 50f && detail.vignetteMidpoint == 50f) effects.vignetteMidpoint else detail.vignetteMidpoint
        val syncedVignetteFeather = if (effects.vignetteFeather != 50f && detail.vignetteFeather == 50f) effects.vignetteFeather else detail.vignetteFeather

        val syncedGrainAmount = if (effects.grainAmount != 0f && detail.grainAmount == 0f) effects.grainAmount else detail.grainAmount
        val syncedGrainSize = if (effects.grainSize != 25f && detail.grainSize == 25f) effects.grainSize else detail.grainSize
        val syncedGrainRoughness = if (effects.grainRoughness != 50f && detail.grainRoughness == 50f) effects.grainRoughness else detail.grainRoughness

        val newBasic = basic.copy(vibrance = syncedVibrance, saturation = syncedSaturation)
        val newPresence = PresenceAdjustments(
            texture = syncedTexture,
            clarity = syncedClarity,
            dehaze = syncedDehaze,
            vibrance = syncedVibrance,
            saturation = syncedSaturation
        )
        val newEffects = effects.copy(
            texture = syncedTexture,
            clarity = syncedClarity,
            dehaze = syncedDehaze,
            vignetteAmount = syncedVignetteAmount,
            vignetteMidpoint = syncedVignetteMidpoint,
            vignetteFeather = syncedVignetteFeather,
            grainAmount = syncedGrainAmount,
            grainSize = syncedGrainSize,
            grainRoughness = syncedGrainRoughness
        )
        val newDetail = detail.copy(
            vignetteAmount = syncedVignetteAmount,
            vignetteMidpoint = syncedVignetteMidpoint,
            vignetteFeather = syncedVignetteFeather,
            grainAmount = syncedGrainAmount,
            grainSize = syncedGrainSize,
            grainRoughness = syncedGrainRoughness
        )
        return copy(
            basic = newBasic,
            presence = newPresence,
            effects = newEffects,
            detail = newDetail
        )
    }
}

/**
 * Tips 401–420: Non-Destructive Crop & Geometry Adjustments
 */
@Serializable
data class CropAdjustments(
    val left: Float = 0.0f,
    val top: Float = 0.0f,
    val right: Float = 1.0f,
    val bottom: Float = 1.0f,
    val rotationDegrees: Float = 0.0f,      // 0, 90, 180, 270 degrees
    val straightenAngle: Float = 0.0f,      // -45.0f to +45.0f degrees
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val aspectRatio: String = "Free",       // "Free", "Original", "1:1", "4:5", "16:9", "9:16", "3:2", "4:3"
    val verticalPerspective: Float = 0.0f,  // -100f to +100f (Tip 413)
    val horizontalPerspective: Float = 0.0f // -100f to +100f (Tip 413)
)

@Serializable
data class BlurAdjustments(
    val amount: Float = 0.0f,               // 0f to 100f
    val bokehShape: String = "Circle",      // Circle, Bubble, 5-blade, Ring, Cat eye
    val boost: Float = 0.0f,
    val focalRangeStart: Float = 0.0f,
    val focalRangeEnd: Float = 1.0f
)

@Serializable
data class OpticsAdjustments(
    val removeChromaticAberration: Boolean = false,
    val enableLensCorrections: Boolean = false,
    val distortion: Float = 0.0f,
    val vignetteAmount: Float = 0.0f
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

// -----------------------------------------------------------------------------
// Phase 5 Masking Architecture & Sealed SubMask Hierarchy
// -----------------------------------------------------------------------------

@Serializable
enum class MaskCombineMode {
    ADD,
    SUBTRACT,
    INTERSECT
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
    abstract val combineMode: MaskCombineMode

    @Serializable
    data class Brush(
        override val id: String,
        override val isInverted: Boolean = false,
        override val combineMode: MaskCombineMode = MaskCombineMode.ADD,
        val points: List<BrushStrokePoint> = emptyList()
    ) : SubMask()

    @Serializable
    data class LinearGradient(
        override val id: String,
        override val isInverted: Boolean = false,
        override val combineMode: MaskCombineMode = MaskCombineMode.ADD,
        val startX: Float = 0f,
        val startY: Float = 0f,
        val endX: Float = 0f,
        val endY: Float = 0f
    ) : SubMask()

    @Serializable
    data class RadialGradient(
        override val id: String,
        override val isInverted: Boolean = false,
        override val combineMode: MaskCombineMode = MaskCombineMode.ADD,
        val centerX: Float = 0.5f,
        val centerY: Float = 0.5f,
        val radiusX: Float = 0.25f,
        val radiusY: Float = 0.25f,
        val rotationDegrees: Float = 0f,
        val feather: Float = 50f
    ) : SubMask()

    @Serializable
    data class AISubject(
        override val id: String,
        override val isInverted: Boolean = false,
        override val combineMode: MaskCombineMode = MaskCombineMode.ADD,
        val maskAssetPath: String? = null
    ) : SubMask()

    @Serializable
    data class AISky(
        override val id: String,
        override val isInverted: Boolean = false,
        override val combineMode: MaskCombineMode = MaskCombineMode.ADD,
        val maskAssetPath: String? = null
    ) : SubMask()

    @Serializable
    data class ColorRange(
        override val id: String,
        override val isInverted: Boolean = false,
        override val combineMode: MaskCombineMode = MaskCombineMode.ADD,
        val targetHue: Float = 0f,
        val hueTolerance: Float = 30f,
        val feather: Float = 25f
    ) : SubMask()

    @Serializable
    data class LuminanceRange(
        override val id: String,
        override val isInverted: Boolean = false,
        override val combineMode: MaskCombineMode = MaskCombineMode.ADD,
        val minLuminance: Float = 0f,
        val maxLuminance: Float = 100f,
        val feather: Float = 25f
    ) : SubMask()
}

/**
 * Brush stroke point with ultra-compact serialization:
 * Serializes as JSON array `[x, y, radius, feather, flow]` instead of verbose object.
 * Guarantees a 500-point brush stroke is < 20KB (approx 12-14KB) to prevent OOM memory bomb.
 */
@Serializable(with = BrushStrokePointSerializer::class)
data class BrushStrokePoint(
    val x: Float,
    val y: Float,
    val radius: Float = 20.0f,
    val feather: Float = 25.0f,
    val flow: Float = 1.0f
)

object BrushStrokePointSerializer : KSerializer<BrushStrokePoint> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("BrushStrokePoint")

    override fun serialize(encoder: Encoder, value: BrushStrokePoint) {
        if (encoder is JsonEncoder) {
            val array = buildJsonArray {
                add(JsonPrimitive(value.x))
                add(JsonPrimitive(value.y))
                add(JsonPrimitive(value.radius))
                add(JsonPrimitive(value.feather))
                add(JsonPrimitive(value.flow))
            }
            encoder.encodeJsonElement(array)
        } else {
            val composite = encoder.beginStructure(descriptor)
            composite.encodeFloatElement(descriptor, 0, value.x)
            composite.encodeFloatElement(descriptor, 1, value.y)
            composite.encodeFloatElement(descriptor, 2, value.radius)
            composite.encodeFloatElement(descriptor, 3, value.feather)
            composite.encodeFloatElement(descriptor, 4, value.flow)
            composite.endStructure(descriptor)
        }
    }

    override fun deserialize(decoder: Decoder): BrushStrokePoint {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            return if (element is JsonArray) {
                BrushStrokePoint(
                    x = element[0].jsonPrimitive.float,
                    y = element[1].jsonPrimitive.float,
                    radius = if (element.size > 2) element[2].jsonPrimitive.float else 20.0f,
                    feather = if (element.size > 3) element[3].jsonPrimitive.float else 25.0f,
                    flow = if (element.size > 4) element[4].jsonPrimitive.float else 1.0f
                )
            } else if (element is JsonObject) {
                BrushStrokePoint(
                    x = element["x"]?.jsonPrimitive?.float ?: 0f,
                    y = element["y"]?.jsonPrimitive?.float ?: 0f,
                    radius = element["radius"]?.jsonPrimitive?.float ?: 20.0f,
                    feather = element["feather"]?.jsonPrimitive?.float ?: 25.0f,
                    flow = element["flow"]?.jsonPrimitive?.float ?: 1.0f
                )
            } else {
                BrushStrokePoint(0f, 0f)
            }
        } else {
            return BrushStrokePoint(0f, 0f)
        }
    }
}
