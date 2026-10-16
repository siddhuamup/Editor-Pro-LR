package com.cleanlr.preset

import com.cleanlr.model.*
import kotlinx.serialization.Serializable

/**
 * Task T6: Preset System (Lightroom Mobile Amount 0–200% Architecture)
 *
 * Defines a curated photographic aesthetic preset with group categorization,
 * full recipe delta, and linear scaling from 0% (subtle/null) to 200% (intense).
 */
@Serializable
data class Preset(
    val id: String,
    val name: String,
    val group: String,
    val recipe: EditRecipe,
    val description: String = ""
)

/**
 * Manages preset library, starter pack, and Amount (0–200%) interpolation engine.
 */
object PresetManager {

    /**
     * Built-in Starter Pack matching Lightroom Mobile curated styles.
     */
    val STARTER_PACK: List<Preset> = listOf(
        // Group: Creative
        Preset(
            id = "preset_teal_orange",
            name = "Teal & Orange",
            group = "Creative",
            description = "Cinematic Hollywood contrast with warm skin tones and teal shadows",
            recipe = EditRecipe(
                basic = BasicAdjustments(
                    exposure = 0.15f,
                    contrast = 20.0f,
                    highlights = -15.0f,
                    shadows = 10.0f,
                    vibrance = 15.0f,
                    saturation = -5.0f
                ),
                effects = EffectsAdjustments(texture = 10f, clarity = 15f),
                colorGrading = ColorGrading(
                    shadows = ColorGradingWheel(hue = 195f, saturation = 35f, luminance = -5f),
                    highlights = ColorGradingWheel(hue = 38f, saturation = 40f, luminance = 5f),
                    blending = 55f,
                    balance = -10f
                )
            )
        ),
        Preset(
            id = "preset_cinematic_mood",
            name = "Cinematic Drama",
            group = "Creative",
            description = "High dynamic punch with moody deep shadows and faded blacks",
            recipe = EditRecipe(
                basic = BasicAdjustments(
                    contrast = 30.0f,
                    highlights = -25.0f,
                    shadows = -15.0f,
                    whites = 10.0f,
                    blacks = 15.0f
                ),
                effects = EffectsAdjustments(dehaze = 15f, clarity = 20f, vignetteAmount = -25f)
            )
        ),

        // Group: B&W
        Preset(
            id = "preset_bw_high_contrast",
            name = "B&W High Contrast",
            group = "B&W",
            description = "Punchy monochrome with crisp whites and velvety blacks",
            recipe = EditRecipe(
                basic = BasicAdjustments(
                    contrast = 40.0f,
                    highlights = -20.0f,
                    shadows = 15.0f,
                    whites = 25.0f,
                    blacks = -20.0f,
                    saturation = -100.0f,
                    vibrance = -100.0f
                ),
                effects = EffectsAdjustments(texture = 15f, clarity = 25f, grainAmount = 15f)
            )
        ),
        Preset(
            id = "preset_bw_soft_film",
            name = "B&W Soft Film",
            group = "B&W",
            description = "Gentle tonal curve with vintage silver gelatin print look",
            recipe = EditRecipe(
                basic = BasicAdjustments(
                    contrast = -10.0f,
                    highlights = -10.0f,
                    shadows = 20.0f,
                    blacks = 25.0f,
                    saturation = -100.0f,
                    vibrance = -100.0f
                ),
                effects = EffectsAdjustments(texture = -5f, grainAmount = 30f, grainSize = 35f)
            )
        ),

        // Group: Portrait
        Preset(
            id = "preset_portrait_soft",
            name = "Soft Portrait",
            group = "Portrait",
            description = "Flattering skin tones with smooth textures and gentle highlights",
            recipe = EditRecipe(
                basic = BasicAdjustments(
                    exposure = 0.20f,
                    contrast = -5.0f,
                    highlights = -20.0f,
                    shadows = 15.0f,
                    temperature = 5700f,
                    tint = 5f,
                    vibrance = 10.0f
                ),
                effects = EffectsAdjustments(texture = -15f, clarity = -10f, vignetteAmount = -15f)
            )
        ),

        // Group: Landscape
        Preset(
            id = "preset_landscape_punch",
            name = "Punchy Landscape",
            group = "Landscape",
            description = "Rich foliage, dynamic sky contrast, and enhanced micro-texture",
            recipe = EditRecipe(
                basic = BasicAdjustments(
                    contrast = 20.0f,
                    highlights = -35.0f,
                    shadows = 25.0f,
                    whites = 15.0f,
                    blacks = -10.0f,
                    vibrance = 35.0f,
                    saturation = 10.0f
                ),
                effects = EffectsAdjustments(texture = 25f, clarity = 20f, dehaze = 20f),
                detail = DetailAdjustments(sharpening = 60f, sharpenRadius = 1.0f, sharpenMasking = 50f)
            )
        ),

        // Group: Vintage
        Preset(
            id = "preset_vintage_warmth",
            name = "Vintage 1970",
            group = "Vintage",
            description = "Warm amber glow with nostalgic matte shadows and film grain",
            recipe = EditRecipe(
                basic = BasicAdjustments(
                    exposure = 0.10f,
                    contrast = -15.0f,
                    shadows = 20.0f,
                    blacks = 25.0f,
                    temperature = 6200f,
                    tint = 12f,
                    saturation = -15.0f
                ),
                effects = EffectsAdjustments(
                    vignetteAmount = -30f,
                    vignetteFeather = 60f,
                    grainAmount = 40f,
                    grainRoughness = 60f
                )
            )
        )
    )

    fun getGroups(): List<String> = STARTER_PACK.map { it.group }.distinct()

    fun getPresetsByGroup(group: String): List<Preset> =
        STARTER_PACK.filter { it.group.equals(group, ignoreCase = true) }

    fun findPresetById(id: String): Preset? =
        STARTER_PACK.find { it.id == id }

    /**
     * Scale a preset against a base recipe using the modern Amount slider (0% to 200%).
     *
     * - amount = 0%: recipe unchanged (baseline)
     * - amount = 100%: 1.0x standard preset application
     * - amount = 200%: 2.0x intensified preset delta
     *
     * @param base The recipe before applying the preset
     * @param preset The target preset
     * @param amount The scaling factor (0 to 200, default 100)
     */
    fun applyPresetWithAmount(
        base: EditRecipe,
        preset: Preset,
        amount: Float = 100.0f
    ): EditRecipe {
        val scale = (amount.coerceIn(0f, 200f)) / 100.0f
        val p = preset.recipe

        fun scaleFloat(baseVal: Float, targetVal: Float, min: Float, max: Float): Float {
            val delta = targetVal - baseVal
            return (baseVal + delta * scale).coerceIn(min, max)
        }

        val scaledBasic = BasicAdjustments(
            exposure = scaleFloat(base.basic.exposure, p.basic.exposure, -5.0f, 5.0f),
            contrast = scaleFloat(base.basic.contrast, p.basic.contrast, -100f, 100f),
            highlights = scaleFloat(base.basic.highlights, p.basic.highlights, -100f, 100f),
            shadows = scaleFloat(base.basic.shadows, p.basic.shadows, -100f, 100f),
            whites = scaleFloat(base.basic.whites, p.basic.whites, -100f, 100f),
            blacks = scaleFloat(base.basic.blacks, p.basic.blacks, -100f, 100f),
            temperature = scaleFloat(base.basic.temperature, p.basic.temperature, 2000f, 50000f),
            tint = scaleFloat(base.basic.tint, p.basic.tint, -150f, 150f),
            vibrance = scaleFloat(base.basic.vibrance, p.basic.vibrance, -100f, 100f),
            saturation = scaleFloat(base.basic.saturation, p.basic.saturation, -100f, 100f)
        )

        val scaledEffects = EffectsAdjustments(
            texture = scaleFloat(base.effects.texture, p.effects.texture, -100f, 100f),
            clarity = scaleFloat(base.effects.clarity, p.effects.clarity, -100f, 100f),
            dehaze = scaleFloat(base.effects.dehaze, p.effects.dehaze, -100f, 100f),
            vignetteAmount = scaleFloat(base.effects.vignetteAmount, p.effects.vignetteAmount, -100f, 100f),
            vignetteMidpoint = scaleFloat(base.effects.vignetteMidpoint, p.effects.vignetteMidpoint, 0f, 100f),
            vignetteFeather = scaleFloat(base.effects.vignetteFeather, p.effects.vignetteFeather, 0f, 100f),
            grainAmount = scaleFloat(base.effects.grainAmount, p.effects.grainAmount, 0f, 100f),
            grainSize = scaleFloat(base.effects.grainSize, p.effects.grainSize, 0f, 100f),
            grainRoughness = scaleFloat(base.effects.grainRoughness, p.effects.grainRoughness, 0f, 100f)
        )

        val scaledDetail = DetailAdjustments(
            sharpening = scaleFloat(base.detail.sharpening, p.detail.sharpening, 0f, 150f),
            sharpenRadius = scaleFloat(base.detail.sharpenRadius, p.detail.sharpenRadius, 0.5f, 3.0f),
            sharpenDetail = scaleFloat(base.detail.sharpenDetail, p.detail.sharpenDetail, 0f, 100f),
            sharpenMasking = scaleFloat(base.detail.sharpenMasking, p.detail.sharpenMasking, 0f, 100f),
            luminanceNoise = scaleFloat(base.detail.luminanceNoise, p.detail.luminanceNoise, 0f, 100f),
            colorNoise = scaleFloat(base.detail.colorNoise, p.detail.colorNoise, 0f, 100f)
        )

        // Interpolate Color Grading if present
        val scaledColorGrading = if (scale == 0f) {
            base.colorGrading
        } else {
            ColorGrading(
                shadows = ColorGradingWheel(
                    hue = p.colorGrading.shadows.hue,
                    saturation = p.colorGrading.shadows.saturation * scale.coerceAtMost(1.5f),
                    luminance = scaleFloat(base.colorGrading.shadows.luminance, p.colorGrading.shadows.luminance, -100f, 100f)
                ),
                highlights = ColorGradingWheel(
                    hue = p.colorGrading.highlights.hue,
                    saturation = p.colorGrading.highlights.saturation * scale.coerceAtMost(1.5f),
                    luminance = scaleFloat(base.colorGrading.highlights.luminance, p.colorGrading.highlights.luminance, -100f, 100f)
                ),
                midtones = ColorGradingWheel(
                    hue = p.colorGrading.midtones.hue,
                    saturation = p.colorGrading.midtones.saturation * scale.coerceAtMost(1.5f),
                    luminance = scaleFloat(base.colorGrading.midtones.luminance, p.colorGrading.midtones.luminance, -100f, 100f)
                ),
                blending = p.colorGrading.blending,
                balance = scaleFloat(base.colorGrading.balance, p.colorGrading.balance, -100f, 100f)
            )
        }

        return base.copy(
            basic = scaledBasic,
            effects = scaledEffects,
            detail = scaledDetail,
            colorGrading = scaledColorGrading
        )
    }
}
