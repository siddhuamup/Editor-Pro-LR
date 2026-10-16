package com.cleanlr.storage

import com.cleanlr.model.*
import java.util.Locale
import java.util.regex.Pattern

/**
 * Task T7 & T9: Bidirectional Adobe Camera Raw (ACR) XMP Converter
 *
 * Implements genuine Adobe Camera Raw Process Version 2012 (PV2012) XMP specification.
 * Converts [EditRecipe] <-> ACR `crs:` XML with defensive fallback for third-party presets.
 */
object AcrXmpConverter {

    /**
     * Serializes an [EditRecipe] to standard Adobe Camera Raw XMP packet.
     */
    fun toXmp(recipe: EditRecipe): String {
        val b = recipe.basic
        val e = recipe.effects
        val d = recipe.detail
        val h = recipe.hsl
        val cg = recipe.colorGrading

        return """
            <x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 7.0-c000 1.000000, 0000/00/00-00:00:00">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                  xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
                  crs:Version="15.4"
                  crs:ProcessVersion="11.0"
                  crs:WhiteBalance="Custom"
                  crs:Temperature="${b.temperature.toInt()}"
                  crs:Tint="${b.tint.toInt()}"
                  crs:Exposure2012="${String.format(Locale.US, "%.2f", b.exposure)}"
                  crs:Contrast2012="${b.contrast.toInt()}"
                  crs:Highlights2012="${b.highlights.toInt()}"
                  crs:Shadows2012="${b.shadows.toInt()}"
                  crs:Whites2012="${b.whites.toInt()}"
                  crs:Blacks2012="${b.blacks.toInt()}"
                  crs:Texture="${e.texture.toInt()}"
                  crs:Clarity2012="${e.clarity.toInt()}"
                  crs:Dehaze="${e.dehaze.toInt()}"
                  crs:Vibrance="${b.vibrance.toInt()}"
                  crs:Saturation="${b.saturation.toInt()}"
                  crs:PostCropVignetteAmount="${e.vignetteAmount.toInt()}"
                  crs:PostCropVignetteMidpoint="${e.vignetteMidpoint.toInt()}"
                  crs:PostCropVignetteFeather="${e.vignetteFeather.toInt()}"
                  crs:GrainAmount="${e.grainAmount.toInt()}"
                  crs:GrainSize="${e.grainSize.toInt()}"
                  crs:GrainRoughness="${e.grainRoughness.toInt()}"
                  crs:Sharpness="${d.sharpening.toInt()}"
                  crs:SharpenRadius="${String.format(Locale.US, "%.1f", d.sharpenRadius)}"
                  crs:SharpenDetail="${d.sharpenDetail.toInt()}"
                  crs:SharpenEdgeMasking="${d.sharpenMasking.toInt()}"
                  crs:LuminanceSmoothing="${d.luminanceNoise.toInt()}"
                  crs:ColorNoiseReduction="${d.colorNoise.toInt()}"
                  crs:HueAdjustmentRed="${h.red.hue.toInt()}"
                  crs:HueAdjustmentOrange="${h.orange.hue.toInt()}"
                  crs:HueAdjustmentYellow="${h.yellow.hue.toInt()}"
                  crs:HueAdjustmentGreen="${h.green.hue.toInt()}"
                  crs:HueAdjustmentAqua="${h.aqua.hue.toInt()}"
                  crs:HueAdjustmentBlue="${h.blue.hue.toInt()}"
                  crs:HueAdjustmentPurple="${h.purple.hue.toInt()}"
                  crs:HueAdjustmentMagenta="${h.magenta.hue.toInt()}"
                  crs:SaturationAdjustmentRed="${h.red.saturation.toInt()}"
                  crs:SaturationAdjustmentOrange="${h.orange.saturation.toInt()}"
                  crs:SaturationAdjustmentYellow="${h.yellow.saturation.toInt()}"
                  crs:SaturationAdjustmentGreen="${h.green.saturation.toInt()}"
                  crs:SaturationAdjustmentAqua="${h.aqua.saturation.toInt()}"
                  crs:SaturationAdjustmentBlue="${h.blue.saturation.toInt()}"
                  crs:SaturationAdjustmentPurple="${h.purple.saturation.toInt()}"
                  crs:SaturationAdjustmentMagenta="${h.magenta.saturation.toInt()}"
                  crs:LuminanceAdjustmentRed="${h.red.luminance.toInt()}"
                  crs:LuminanceAdjustmentOrange="${h.orange.luminance.toInt()}"
                  crs:LuminanceAdjustmentYellow="${h.yellow.luminance.toInt()}"
                  crs:LuminanceAdjustmentGreen="${h.green.luminance.toInt()}"
                  crs:LuminanceAdjustmentAqua="${h.aqua.luminance.toInt()}"
                  crs:LuminanceAdjustmentBlue="${h.blue.luminance.toInt()}"
                  crs:LuminanceAdjustmentPurple="${h.purple.luminance.toInt()}"
                  crs:LuminanceAdjustmentMagenta="${h.magenta.luminance.toInt()}"
                  crs:ColorGradeShadowHue="${cg.shadows.hue.toInt()}"
                  crs:ColorGradeShadowSat="${cg.shadows.saturation.toInt()}"
                  crs:ColorGradeShadowLum="${cg.shadows.luminance.toInt()}"
                  crs:ColorGradeMidtoneHue="${cg.midtones.hue.toInt()}"
                  crs:ColorGradeMidtoneSat="${cg.midtones.saturation.toInt()}"
                  crs:ColorGradeMidtoneLum="${cg.midtones.luminance.toInt()}"
                  crs:ColorGradeHighlightHue="${cg.highlights.hue.toInt()}"
                  crs:ColorGradeHighlightSat="${cg.highlights.saturation.toInt()}"
                  crs:ColorGradeHighlightLum="${cg.highlights.luminance.toInt()}"
                  crs:ColorGradeBlending="${cg.blending.toInt()}"
                  crs:ColorGradeBalance="${cg.balance.toInt()}">
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()
    }

    /**
     * Parses an Adobe Camera Raw XMP packet into an [EditRecipe].
     * Supports both attribute syntax (`crs:Exposure2012="0.5"`) and element tag syntax.
     */
    fun fromXmp(xmpString: String, base: EditRecipe = EditRecipe()): EditRecipe {
        fun extractFloat(attr: String, default: Float): Float {
            // Match crs:Attr="value" or <crs:Attr>value</crs:Attr>
            val attrPattern = Pattern.compile("crs:$attr\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
            val matcher = attrPattern.matcher(xmpString)
            if (matcher.find()) {
                val valueStr = matcher.group(1)?.trim() ?: return default
                return valueStr.toFloatOrNull() ?: default
            }

            val tagPattern = Pattern.compile("<crs:$attr>([^<]+)</crs:$attr>", Pattern.CASE_INSENSITIVE)
            val tagMatcher = tagPattern.matcher(xmpString)
            if (tagMatcher.find()) {
                val valueStr = tagMatcher.group(1)?.trim() ?: return default
                return valueStr.toFloatOrNull() ?: default
            }

            return default
        }

        val parsedBasic = BasicAdjustments(
            exposure = extractFloat("Exposure2012", base.basic.exposure),
            contrast = extractFloat("Contrast2012", base.basic.contrast),
            highlights = extractFloat("Highlights2012", base.basic.highlights),
            shadows = extractFloat("Shadows2012", base.basic.shadows),
            whites = extractFloat("Whites2012", base.basic.whites),
            blacks = extractFloat("Blacks2012", base.basic.blacks),
            temperature = extractFloat("Temperature", base.basic.temperature),
            tint = extractFloat("Tint", base.basic.tint),
            vibrance = extractFloat("Vibrance", base.basic.vibrance),
            saturation = extractFloat("Saturation", base.basic.saturation)
        )

        val parsedEffects = EffectsAdjustments(
            texture = extractFloat("Texture", base.effects.texture),
            clarity = extractFloat("Clarity2012", base.effects.clarity),
            dehaze = extractFloat("Dehaze", base.effects.dehaze),
            vignetteAmount = extractFloat("PostCropVignetteAmount", base.effects.vignetteAmount),
            vignetteMidpoint = extractFloat("PostCropVignetteMidpoint", base.effects.vignetteMidpoint),
            vignetteFeather = extractFloat("PostCropVignetteFeather", base.effects.vignetteFeather),
            grainAmount = extractFloat("GrainAmount", base.effects.grainAmount),
            grainSize = extractFloat("GrainSize", base.effects.grainSize),
            grainRoughness = extractFloat("GrainRoughness", base.effects.grainRoughness)
        )

        val parsedDetail = DetailAdjustments(
            sharpening = extractFloat("Sharpness", base.detail.sharpening),
            sharpenRadius = extractFloat("SharpenRadius", base.detail.sharpenRadius),
            sharpenDetail = extractFloat("SharpenDetail", base.detail.sharpenDetail),
            sharpenMasking = extractFloat("SharpenEdgeMasking", base.detail.sharpenMasking),
            luminanceNoise = extractFloat("LuminanceSmoothing", base.detail.luminanceNoise),
            colorNoise = extractFloat("ColorNoiseReduction", base.detail.colorNoise)
        )

        val parsedHsl = HslAdjustments(
            red = HslValues(
                hue = extractFloat("HueAdjustmentRed", base.hsl.red.hue),
                saturation = extractFloat("SaturationAdjustmentRed", base.hsl.red.saturation),
                luminance = extractFloat("LuminanceAdjustmentRed", base.hsl.red.luminance)
            ),
            orange = HslValues(
                hue = extractFloat("HueAdjustmentOrange", base.hsl.orange.hue),
                saturation = extractFloat("SaturationAdjustmentOrange", base.hsl.orange.saturation),
                luminance = extractFloat("LuminanceAdjustmentOrange", base.hsl.orange.luminance)
            ),
            yellow = HslValues(
                hue = extractFloat("HueAdjustmentYellow", base.hsl.yellow.hue),
                saturation = extractFloat("SaturationAdjustmentYellow", base.hsl.yellow.saturation),
                luminance = extractFloat("LuminanceAdjustmentYellow", base.hsl.yellow.luminance)
            ),
            green = HslValues(
                hue = extractFloat("HueAdjustmentGreen", base.hsl.green.hue),
                saturation = extractFloat("SaturationAdjustmentGreen", base.hsl.green.saturation),
                luminance = extractFloat("LuminanceAdjustmentGreen", base.hsl.green.luminance)
            ),
            aqua = HslValues(
                hue = extractFloat("HueAdjustmentAqua", base.hsl.aqua.hue),
                saturation = extractFloat("SaturationAdjustmentAqua", base.hsl.aqua.saturation),
                luminance = extractFloat("LuminanceAdjustmentAqua", base.hsl.aqua.luminance)
            ),
            blue = HslValues(
                hue = extractFloat("HueAdjustmentBlue", base.hsl.blue.hue),
                saturation = extractFloat("SaturationAdjustmentBlue", base.hsl.blue.saturation),
                luminance = extractFloat("LuminanceAdjustmentBlue", base.hsl.blue.luminance)
            ),
            purple = HslValues(
                hue = extractFloat("HueAdjustmentPurple", base.hsl.purple.hue),
                saturation = extractFloat("SaturationAdjustmentPurple", base.hsl.purple.saturation),
                luminance = extractFloat("LuminanceAdjustmentPurple", base.hsl.purple.luminance)
            ),
            magenta = HslValues(
                hue = extractFloat("HueAdjustmentMagenta", base.hsl.magenta.hue),
                saturation = extractFloat("SaturationAdjustmentMagenta", base.hsl.magenta.saturation),
                luminance = extractFloat("LuminanceAdjustmentMagenta", base.hsl.magenta.luminance)
            )
        )

        val parsedColorGrading = ColorGrading(
            shadows = ColorGradingWheel(
                hue = extractFloat("ColorGradeShadowHue", base.colorGrading.shadows.hue),
                saturation = extractFloat("ColorGradeShadowSat", base.colorGrading.shadows.saturation),
                luminance = extractFloat("ColorGradeShadowLum", base.colorGrading.shadows.luminance)
            ),
            midtones = ColorGradingWheel(
                hue = extractFloat("ColorGradeMidtoneHue", base.colorGrading.midtones.hue),
                saturation = extractFloat("ColorGradeMidtoneSat", base.colorGrading.midtones.saturation),
                luminance = extractFloat("ColorGradeMidtoneLum", base.colorGrading.midtones.luminance)
            ),
            highlights = ColorGradingWheel(
                hue = extractFloat("ColorGradeHighlightHue", base.colorGrading.highlights.hue),
                saturation = extractFloat("ColorGradeHighlightSat", base.colorGrading.highlights.saturation),
                luminance = extractFloat("ColorGradeHighlightLum", base.colorGrading.highlights.luminance)
            ),
            blending = extractFloat("ColorGradeBlending", base.colorGrading.blending),
            balance = extractFloat("ColorGradeBalance", base.colorGrading.balance)
        )

        return base.copy(
            basic = parsedBasic,
            effects = parsedEffects,
            detail = parsedDetail,
            hsl = parsedHsl,
            colorGrading = parsedColorGrading
        )
    }
}
