package com.cleanlr.schema

import com.cleanlr.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Task T2: Missing Models Test
 *
 * Verifies that all models specified in Document 5 are fully implemented and accurate:
 * 1. 4-Channel ToneCurves (composite, red, green, blue)
 * 2. 8-Channel HslAdjustments (red, orange, yellow, green, aqua, blue, purple, magenta)
 * 3. 4-Wheel ColorGrading (shadows, midtones, highlights, global) + blending & balance
 * 4. EffectsAdjustments (texture, clarity, dehaze, vignette, grain)
 * 5. DetailAdjustments (sharpening, radius, detail, masking, luminanceNoise, colorNoise)
 * 6. BasicAdjustments (exposure, contrast, highlights, shadows, whites, blacks, kelvin, tint, vibrance, saturation)
 * 7. JSON serialization roundtrip with zero data drift
 */
class SchemaT2MissingModelsTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `test ToneCurves 4-channel RGB architecture adheres to Document 5`() {
        val defaultCurves = ToneCurves.default()
        assertEquals(2, defaultCurves.composite.size)
        assertEquals(2, defaultCurves.red.size)
        assertEquals(2, defaultCurves.green.size)
        assertEquals(2, defaultCurves.blue.size)

        assertEquals(0f, defaultCurves.composite.first().x, 1e-4f)
        assertEquals(0f, defaultCurves.composite.first().y, 1e-4f)
        assertEquals(255f, defaultCurves.composite.last().x, 1e-4f)
        assertEquals(255f, defaultCurves.composite.last().y, 1e-4f)

        // Custom S-curve test on red channel
        val customRed = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(64f, 48f),
            CurvePoint(192f, 210f),
            CurvePoint(255f, 255f)
        )
        val customCurves = defaultCurves.copy(red = customRed)
        assertEquals(4, customCurves.red.size)
        assertEquals(48f, customCurves.red[1].y, 1e-4f)
        assertEquals(210f, customCurves.red[2].y, 1e-4f)
    }

    @Test
    fun `test 8-channel HSL color mixer model integrity`() {
        val defaultHsl = HslAdjustments.default()
        assertEquals(0f, defaultHsl.red.hue, 1e-4f)
        assertEquals(0f, defaultHsl.orange.saturation, 1e-4f)
        assertEquals(0f, defaultHsl.yellow.luminance, 1e-4f)
        assertEquals(0f, defaultHsl.green.hue, 1e-4f)
        assertEquals(0f, defaultHsl.aqua.saturation, 1e-4f)
        assertEquals(0f, defaultHsl.blue.luminance, 1e-4f)
        assertEquals(0f, defaultHsl.purple.hue, 1e-4f)
        assertEquals(0f, defaultHsl.magenta.saturation, 1e-4f)

        // Custom HSL adjustments
        val customHsl = defaultHsl.copy(
            blue = HslValues(hue = 15f, saturation = -20f, luminance = 10f),
            orange = HslValues(hue = -5f, saturation = 30f, luminance = 15f)
        )
        assertEquals(15f, customHsl.blue.hue, 1e-4f)
        assertEquals(-20f, customHsl.blue.saturation, 1e-4f)
        assertEquals(10f, customHsl.blue.luminance, 1e-4f)
        assertEquals(30f, customHsl.orange.saturation, 1e-4f)
    }

    @Test
    fun `test ColorGrading 4-wheel system includes global wheel and blending controls`() {
        val defaultGrading = ColorGrading.default()
        assertEquals(50f, defaultGrading.blending, 1e-4f)
        assertEquals(0f, defaultGrading.balance, 1e-4f)

        // Verify global wheel exists alongside 3-way wheels
        assertNotNull(defaultGrading.global)
        assertEquals(0f, defaultGrading.global.hue, 1e-4f)
        assertEquals(0f, defaultGrading.global.saturation, 1e-4f)
        assertEquals(0f, defaultGrading.global.luminance, 1e-4f)

        val customGrading = defaultGrading.copy(
            shadows = ColorGradingWheel(hue = 210f, saturation = 25f, luminance = -10f),
            highlights = ColorGradingWheel(hue = 45f, saturation = 20f, luminance = 5f),
            global = ColorGradingWheel(hue = 30f, saturation = 10f, luminance = 0f),
            blending = 75f,
            balance = -15f
        )
        assertEquals(210f, customGrading.shadows.hue, 1e-4f)
        assertEquals(45f, customGrading.highlights.hue, 1e-4f)
        assertEquals(30f, customGrading.global.hue, 1e-4f)
        assertEquals(75f, customGrading.blending, 1e-4f)
        assertEquals(-15f, customGrading.balance, 1e-4f)
    }

    @Test
    fun `test EffectsAdjustments and DetailAdjustments default contracts`() {
        val effects = EffectsAdjustments()
        assertEquals(0f, effects.texture, 1e-4f)
        assertEquals(0f, effects.clarity, 1e-4f)
        assertEquals(0f, effects.dehaze, 1e-4f)
        assertEquals(0f, effects.vignetteAmount, 1e-4f)
        assertEquals(50f, effects.vignetteMidpoint, 1e-4f)
        assertEquals(50f, effects.vignetteFeather, 1e-4f)
        assertEquals(0f, effects.grainAmount, 1e-4f)
        assertEquals(25f, effects.grainSize, 1e-4f)
        assertEquals(50f, effects.grainRoughness, 1e-4f)

        val detail = DetailAdjustments()
        assertEquals(0f, detail.sharpening, 1e-4f)
        assertEquals(1.0f, detail.sharpenRadius, 1e-4f)
        assertEquals(25f, detail.sharpenDetail, 1e-4f)
        assertEquals(0f, detail.sharpenMasking, 1e-4f)
        assertEquals(0f, detail.luminanceNoise, 1e-4f)
        assertEquals(25f, detail.colorNoise, 1e-4f)
    }

    @Test
    fun `test complete EditRecipe JSON serialization round-trip with all Document 5 models`() {
        val recipe = EditRecipe(
            version = 1,
            assetId = "img_doc5_test",
            isHdrEnabled = true,
            basic = BasicAdjustments(
                exposure = 1.25f,
                contrast = 25f,
                highlights = -35f,
                shadows = 40f,
                whites = -15f,
                blacks = 10f,
                temperature = 6200f,
                tint = 8f,
                vibrance = 20f,
                saturation = -5f
            ),
            effects = EffectsAdjustments(
                texture = 15f,
                clarity = 25f,
                dehaze = 10f,
                vignetteAmount = -25f,
                grainAmount = 15f
            ),
            detail = DetailAdjustments(
                sharpening = 60f,
                sharpenRadius = 1.2f,
                sharpenDetail = 30f,
                colorNoise = 35f
            ),
            curves = ToneCurves.default().copy(
                red = listOf(CurvePoint(0f, 0f), CurvePoint(128f, 140f), CurvePoint(255f, 255f))
            ),
            hsl = HslAdjustments.default().copy(
                blue = HslValues(hue = 10f, saturation = 20f, luminance = -5f)
            ),
            colorGrading = ColorGrading.default().copy(
                global = ColorGradingWheel(hue = 50f, saturation = 15f)
            )
        )

        val encoded = json.encodeToString(EditRecipe.serializer(), recipe)
        assertTrue(encoded.contains("\"version\":1"))
        assertTrue(encoded.contains("\"isHdrEnabled\":true"))
        assertTrue(encoded.contains("\"vibrance\":20.0"))
        assertTrue(encoded.contains("\"global\""))
        assertTrue(encoded.contains("\"composite\""))

        val decoded = json.decodeFromString(EditRecipe.serializer(), encoded)
        assertEquals(recipe.version, decoded.version)
        assertEquals(recipe.assetId, decoded.assetId)
        assertEquals(recipe.isHdrEnabled, decoded.isHdrEnabled)
        assertEquals(recipe.basic.exposure, decoded.basic.exposure, 1e-4f)
        assertEquals(recipe.basic.vibrance, decoded.basic.vibrance, 1e-4f)
        assertEquals(recipe.effects.clarity, decoded.effects.clarity, 1e-4f)
        assertEquals(recipe.detail.colorNoise, decoded.detail.colorNoise, 1e-4f)
        assertEquals(recipe.colorGrading.global.hue, decoded.colorGrading.global.hue, 1e-4f)
        assertEquals(3, decoded.curves.red.size)
        assertEquals(140f, decoded.curves.red[1].y, 1e-4f)
        assertEquals(10f, decoded.hsl.blue.hue, 1e-4f)
    }
}
