package com.cleanlr.schema

import com.cleanlr.model.*
import com.cleanlr.preset.MinedPresetLibrary
import com.cleanlr.preset.Preset
import com.cleanlr.preset.PresetManager
import com.cleanlr.preset.PresetPreviewEngine
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Verifies Complete Preset Panel Color Science & Mined Assets:
 * 1. All 30 original Adobe Lightroom Premium `.webp` thumbnails exist in assets/preset_thumbs/.
 * 2. Complete 531-preset mined catalog loads cleanly with Curve, HSL, Color Grading, Blur & Adaptive values.
 * 3. PresetManager.applyPresetWithAmount interpolates HSL, Tone Curves, Color Grading, Lens Blur & Optics accurately.
 */
class PresetColorScienceTest {

    @Test
    fun testAll30AuthenticAdobePremiumThumbnailsExistInAssets() {
        val thumbsDir = File("src/main/assets/preset_thumbs").let {
            if (it.exists()) it else File("app/src/main/assets/preset_thumbs")
        }
        assertTrue("preset_thumbs directory must exist at ${thumbsDir.absolutePath}", thumbsDir.exists())
        for ((collectionName, fileName) in PresetPreviewEngine.PREMIUM_COLLECTION_THUMBS) {
            val f = File(thumbsDir, fileName)
            assertTrue("Missing mined Adobe thumbnail '$fileName' for '$collectionName'", f.exists() && f.length() > 1000L)
        }
        assertEquals(30, PresetPreviewEngine.PREMIUM_COLLECTION_THUMBS.size)
    }

    @Test
    fun testMinedPresetsJsonContainsFullColorSciencePresets() {
        val jsonFile = File("src/main/assets/mined_presets.json").let {
            if (it.exists()) it else File("app/src/main/assets/mined_presets.json")
        }
        assertTrue("mined_presets.json must exist", jsonFile.exists())
        val loaded = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }.decodeFromString<List<Preset>>(jsonFile.readText())

        assertTrue("Expected 500+ mined presets, got ${loaded.size}", loaded.size >= 500)

        // Verify Adaptive: Portrait has real adjustments (not empty defaults)
        val adaptivePortraits = loaded.filter { it.group.equals("Adaptive: Portrait", ignoreCase = true) }
        assertTrue("Expected Adaptive: Portrait presets", adaptivePortraits.size >= 10)
        assertTrue(
            "Adaptive: Portrait presets must contain real color-science adjustments",
            adaptivePortraits.any { p ->
                val b = p.recipe.basic
                val e = p.recipe.effects
                b.exposure != 0f || b.contrast != 0f || b.shadows != 0f || e.clarity != 0f || e.texture != 0f
            }
        )

        // Verify Adaptive: Blur Background has real LensBlur amount > 0 (except authentic "Reset Blur")
        val adaptiveBlurs = loaded.filter { it.group.equals("Adaptive: Blur Background", ignoreCase = true) }
        assertTrue("Expected Adaptive: Blur Background presets", adaptiveBlurs.size >= 10)
        val nonResetBlurs = adaptiveBlurs.filter { !it.name.contains("Reset", ignoreCase = true) }
        assertTrue("Expected at least 12 active blur presets", nonResetBlurs.size >= 12)
        assertTrue("Active Adaptive: Blur Background presets must carry blur.amount > 0", nonResetBlurs.all { it.recipe.blur.amount > 0f })

        // Verify Curve presets have non-identity Tone Curves
        val curves = loaded.filter { it.group.equals("Curve", ignoreCase = true) }
        assertTrue("Expected Curve presets", curves.size >= 4)
        assertTrue(
            "Curve presets must contain non-identity RGB/Composite curves",
            curves.any { p ->
                p.recipe.curves.composite.size > 2 || p.recipe.curves.red.size > 2 || p.recipe.curves.blue.size > 2
            }
        )
    }

    @Test
    fun testApplyPresetWithAmountInterpolatesAllColorScienceDomains() {
        val base = EditRecipe()
        val richPreset = Preset(
            id = "test_full_science",
            name = "Full Science Test",
            group = "Style: Cinematic",
            recipe = EditRecipe(
                basic = BasicAdjustments(exposure = 1.0f, contrast = 40f, saturation = -20f),
                effects = EffectsAdjustments(clarity = 20f, vignetteAmount = -30f),
                hsl = HslAdjustments(
                    orange = HslValues(hue = -10f, saturation = 20f, luminance = 15f),
                    blue = HslValues(hue = -25f, saturation = -30f, luminance = -10f)
                ),
                curves = ToneCurves(
                    composite = listOf(CurvePoint(0f, 16f), CurvePoint(128f, 140f), CurvePoint(255f, 245f)),
                    parametricShadows = 20f
                ),
                colorGrading = ColorGrading(
                    shadows = ColorGradingWheel(hue = 200f, saturation = 40f, luminance = -10f),
                    global = ColorGradingWheel(hue = 45f, saturation = 20f, luminance = 5f)
                ),
                blur = BlurAdjustments(amount = 60f, bokehShape = "Bubble", boost = 50f),
                optics = OpticsAdjustments(removeChromaticAberration = true, enableLensCorrections = true)
            )
        )

        // At 50% amount: all deltas must halve accurately
        val half = PresetManager.applyPresetWithAmount(base, richPreset, amount = 50f)
        assertEquals(0.5f, half.basic.exposure, 0.01f)
        assertEquals(20f, half.basic.contrast, 0.01f)
        assertEquals(-5f, half.hsl.orange.hue, 0.01f)
        assertEquals(10f, half.hsl.orange.saturation, 0.01f)
        assertEquals(-12.5f, half.hsl.blue.hue, 0.01f)
        assertEquals(8f, half.curves.composite[0].y, 0.01f)
        assertEquals(134f, half.curves.composite[1].y, 0.01f)
        assertEquals(10f, half.curves.parametricShadows, 0.01f)
        assertEquals(20f, half.colorGrading.shadows.saturation, 0.01f)
        assertEquals(10f, half.colorGrading.global.saturation, 0.01f)
        assertEquals(30f, half.blur.amount, 0.01f)
        assertEquals("Bubble", half.blur.bokehShape)
        assertTrue(half.optics.removeChromaticAberration)

        // At 0% amount: returns unmodified baseline
        val zero = PresetManager.applyPresetWithAmount(base, richPreset, amount = 0f)
        assertEquals(0f, zero.basic.exposure, 0.001f)
        assertEquals(0f, zero.hsl.orange.hue, 0.001f)
        assertEquals(0f, zero.blur.amount, 0.001f)
    }
}
