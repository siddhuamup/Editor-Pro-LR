package com.cleanlr.schema

import com.cleanlr.model.BasicAdjustments
import com.cleanlr.model.EditRecipe
import com.cleanlr.preset.PresetManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Task T6: Preset System & Amount 0–200% Scaling Test
 */
class SchemaT6PresetTest {

    private val neutralRecipe = EditRecipe(
        basic = BasicAdjustments(
            exposure = 0.0f,
            contrast = 0.0f,
            highlights = 0.0f,
            shadows = 0.0f,
            whites = 0.0f,
            blacks = 0.0f,
            temperature = 5500.0f,
            tint = 0.0f,
            vibrance = 0.0f,
            saturation = 0.0f
        )
    )

    @Test
    fun testStarterPackContainsExpectedGroups() {
        val groups = PresetManager.getGroups()
        assertTrue("Should contain Creative group", groups.contains("Creative"))
        assertTrue("Should contain B&W group", groups.contains("B&W"))
        assertTrue("Should contain Portrait group", groups.contains("Portrait"))
        assertTrue("Should contain Landscape group", groups.contains("Landscape"))
        assertTrue("Should contain Vintage group", groups.contains("Vintage"))
    }

    @Test
    fun testFindPresetById() {
        val tealOrange = PresetManager.findPresetById("preset_teal_orange")
        assertNotNull("Teal & Orange preset should be present", tealOrange)
        assertEquals("Teal & Orange", tealOrange!!.name)
        assertEquals("Creative", tealOrange.group)
    }

    @Test
    fun testAmountZeroPercentLeavesRecipeUnchanged() {
        val preset = PresetManager.findPresetById("preset_bw_high_contrast")!!
        val result = PresetManager.applyPresetWithAmount(neutralRecipe, preset, amount = 0.0f)

        assertEquals("Exposure at 0% should match baseline", neutralRecipe.basic.exposure, result.basic.exposure, 0.001f)
        assertEquals("Contrast at 0% should match baseline", neutralRecipe.basic.contrast, result.basic.contrast, 0.001f)
        assertEquals("Saturation at 0% should match baseline", neutralRecipe.basic.saturation, result.basic.saturation, 0.001f)
    }

    @Test
    fun testAmount100PercentAppliesFullPreset() {
        val preset = PresetManager.findPresetById("preset_bw_high_contrast")!!
        val result = PresetManager.applyPresetWithAmount(neutralRecipe, preset, amount = 100.0f)

        assertEquals("Contrast at 100%", preset.recipe.basic.contrast, result.basic.contrast, 0.001f)
        assertEquals("Saturation at 100%", -100f, result.basic.saturation, 0.001f)
        assertEquals("Texture at 100%", preset.recipe.effects.texture, result.effects.texture, 0.001f)
    }

    @Test
    fun testAmount200PercentDoublesDelta() {
        val preset = PresetManager.findPresetById("preset_cinematic_mood")!!
        // preset has contrast = 30.0f (delta = +30)
        val result = PresetManager.applyPresetWithAmount(neutralRecipe, preset, amount = 200.0f)

        assertEquals("Contrast at 200% should double delta: 0 + 30*2 = 60", 60.0f, result.basic.contrast, 0.01f)
        // preset has clarity = 20.0f (delta = +20) -> 200% = 40.0f
        assertEquals("Clarity at 200% should be 40", 40.0f, result.effects.clarity, 0.01f)
    }

    @Test
    fun testAmount200PercentClampsToLegalBounds() {
        val preset = PresetManager.findPresetById("preset_bw_high_contrast")!!
        // contrast is 40.0f. At 200%, 40*2 = 80 (within [-100, 100]).
        // saturation is -100. At 200%, -100*2 = -200, which must clamp to -100.
        val result = PresetManager.applyPresetWithAmount(neutralRecipe, preset, amount = 200.0f)

        assertEquals("Saturation should clamp at -100f", -100.0f, result.basic.saturation, 0.001f)
    }
}
