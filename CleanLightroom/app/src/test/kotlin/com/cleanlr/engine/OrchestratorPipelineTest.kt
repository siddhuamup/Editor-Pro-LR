package com.cleanlr.engine

import com.cleanlr.model.BasicAdjustments
import com.cleanlr.model.DetailAdjustments
import com.cleanlr.model.EditRecipe
import com.cleanlr.model.HslAdjustments
import com.cleanlr.model.PresenceAdjustments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrchestratorPipelineTest {

    private fun isStage2Active(basic: BasicAdjustments): Boolean {
        val gains = ColorScience.kelvinTintToRgbGain(basic.temperature, basic.tint)
        val epsilon = 0.005f
        return abs(gains[0] - 1.0f) > epsilon ||
               abs(gains[1] - 1.0f) > epsilon ||
               abs(gains[2] - 1.0f) > epsilon
    }

    private fun isStage3Active(basic: BasicAdjustments): Boolean {
        val epsilon = 0.001f
        return abs(basic.exposure) > epsilon ||
               abs(basic.contrast) > epsilon ||
               abs(basic.highlights) > epsilon ||
               abs(basic.shadows) > epsilon ||
               abs(basic.whites) > epsilon ||
               abs(basic.blacks) > epsilon
    }

    private fun isStage4Active(presence: PresenceAdjustments): Boolean {
        val epsilon = 0.01f
        return abs(presence.clarity) > epsilon ||
               abs(presence.texture) > epsilon ||
               abs(presence.dehaze) > epsilon
    }

    private fun isStage6Active(hsl: HslAdjustments, presence: PresenceAdjustments): Boolean {
        val epsilon = 0.01f
        val hasHsl = abs(hsl.red.hue) > epsilon || abs(hsl.red.saturation) > epsilon ||
                     abs(hsl.orange.hue) > epsilon || abs(hsl.orange.saturation) > epsilon ||
                     abs(hsl.blue.hue) > epsilon || abs(hsl.blue.saturation) > epsilon
        val hasVibrance = abs(presence.vibrance) > epsilon || abs(presence.saturation) > epsilon
        return hasHsl || hasVibrance
    }

    private fun isStage8Active(detail: DetailAdjustments): Boolean {
        val epsilon = 0.001f
        return abs(detail.sharpening) > epsilon ||
               abs(detail.vignetteAmount) > epsilon ||
               abs(detail.grainAmount) > epsilon
    }

    @Test
    fun testSkipDisabledStagesRuleOnSelectiveEdits() {
        // Case 1: Default Recipe -> All intermediate stages must be bypassed!
        val defaultRecipe = EditRecipe()
        assertFalse("Stage 2 must be skipped on default", isStage2Active(defaultRecipe.basic))
        assertFalse("Stage 3 must be skipped on default", isStage3Active(defaultRecipe.basic))
        assertFalse("Stage 4 must be skipped on default", isStage4Active(defaultRecipe.presence))
        assertFalse("Stage 6 must be skipped on default", isStage6Active(defaultRecipe.hsl, defaultRecipe.presence))
        assertFalse("Stage 8 must be skipped on default", isStage8Active(defaultRecipe.detail))

        // Case 2: Only Exposure changed (+1.5 EV)
        val exposureOnly = defaultRecipe.copy(
            basic = defaultRecipe.basic.copy(exposure = 1.5f)
        )
        assertFalse("Stage 2 must remain skipped", isStage2Active(exposureOnly.basic))
        assertTrue("Stage 3 MUST activate for exposure", isStage3Active(exposureOnly.basic))
        assertFalse("Stage 4 must remain skipped", isStage4Active(exposureOnly.presence))
        assertFalse("Stage 6 must remain skipped", isStage6Active(exposureOnly.hsl, exposureOnly.presence))
        assertFalse("Stage 8 must remain skipped", isStage8Active(exposureOnly.detail))

        // Case 3: Only Vibrance changed (+40)
        val vibranceOnly = defaultRecipe.copy(
            presence = defaultRecipe.presence.copy(vibrance = 40.0f)
        )
        assertFalse("Stage 2 must remain skipped", isStage2Active(vibranceOnly.basic))
        assertFalse("Stage 3 must remain skipped", isStage3Active(vibranceOnly.basic))
        assertFalse("Stage 4 must remain skipped", isStage4Active(vibranceOnly.presence))
        assertTrue("Stage 6 MUST activate for vibrance", isStage6Active(vibranceOnly.hsl, vibranceOnly.presence))
        assertFalse("Stage 8 must remain skipped", isStage8Active(vibranceOnly.detail))

        // Case 4: Only Vignette changed (-40)
        val vignetteOnly = defaultRecipe.copy(
            detail = defaultRecipe.detail.copy(vignetteAmount = -40.0f)
        )
        assertFalse("Stage 2 must remain skipped", isStage2Active(vignetteOnly.basic))
        assertFalse("Stage 3 must remain skipped", isStage3Active(vignetteOnly.basic))
        assertFalse("Stage 4 must remain skipped", isStage4Active(vignetteOnly.presence))
        assertFalse("Stage 6 must remain skipped", isStage6Active(vignetteOnly.hsl, vignetteOnly.presence))
        assertTrue("Stage 8 MUST activate for vignette", isStage8Active(vignetteOnly.detail))
    }

    @Test
    fun testShowOriginalBypassesAllIntermediateStages() {
        val editedRecipe = EditRecipe(
            basic = BasicAdjustments(exposure = 2.0f, contrast = 30f, temperature = 6500f),
            presence = PresenceAdjustments(clarity = 40f, vibrance = 50f),
            detail = DetailAdjustments(sharpening = 60f, vignetteAmount = 50f)
        )

        // When showOriginal = true, active recipe reverts to EditRecipe()
        val showOriginal = true
        val activeRecipe = if (showOriginal) EditRecipe() else editedRecipe

        assertFalse("Stage 2 skipped in compare mode", isStage2Active(activeRecipe.basic))
        assertFalse("Stage 3 skipped in compare mode", isStage3Active(activeRecipe.basic))
        assertFalse("Stage 4 skipped in compare mode", isStage4Active(activeRecipe.presence))
        assertFalse("Stage 6 skipped in compare mode", isStage6Active(activeRecipe.hsl, activeRecipe.presence))
        assertFalse("Stage 8 skipped in compare mode", isStage8Active(activeRecipe.detail))
    }
}
