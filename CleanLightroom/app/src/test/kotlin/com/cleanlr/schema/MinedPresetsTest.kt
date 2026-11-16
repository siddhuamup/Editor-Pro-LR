package com.cleanlr.schema

import com.cleanlr.model.BasicAdjustments
import com.cleanlr.model.EditRecipe
import com.cleanlr.preset.MinedPresetLibrary
import com.cleanlr.preset.PresetManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Verification test for authentic mined presets from Adobe Lightroom APK.
 */
class MinedPresetsTest {

    @Test
    fun testMinedPresetsNonEmpty() {
        assertTrue("Mined presets should contain over 500 presets", MinedPresetLibrary.PRESETS.size >= 500)
    }

    @Test
    fun testAuthenticPresetGroupsPresent() {
        val groups = MinedPresetLibrary.getGroups()
        assertTrue("Should contain Adaptive: Portrait", groups.contains("Adaptive: Portrait"))
        assertTrue("Should contain Adaptive: Sky", groups.contains("Adaptive: Sky"))
        assertTrue("Should contain Style: Cinematic", groups.contains("Style: Cinematic"))
        assertTrue("Should contain Style: Vintage", groups.contains("Style: Vintage"))
        assertTrue("Should contain Color", groups.contains("Color"))
        assertTrue("Should contain B&W", groups.contains("B&W"))
    }

    @Test
    fun testPresetManagerIntegratesMinedPresets() {
        val all = PresetManager.ALL_PRESETS
        assertTrue("PresetManager should integrate 500+ presets", all.size >= 500)

        // Check finding by ID
        val p = PresetManager.findPresetById("mined_style_cinematic_cn01")
        assertNotNull("Cinematic CN01 preset should exist", p)
        assertEquals("Style: Cinematic", p!!.group)
    }

    @Test
    fun testApplyMinedPresetWithAmount() {
        val p = PresetManager.findPresetById("mined_style_cinematic_cn01")!!
        val base = EditRecipe(basic = BasicAdjustments())

        val res50 = PresetManager.applyPresetWithAmount(base, p, amount = 50f)
        val res100 = PresetManager.applyPresetWithAmount(base, p, amount = 100f)

        assertNotNull(res50)
        assertNotNull(res100)
        assertEquals(p.recipe.basic.whites, res100.basic.whites, 0.01f)
    }
}
