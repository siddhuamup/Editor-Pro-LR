package com.cleanlr.schema

import com.cleanlr.model.*
import com.cleanlr.storage.AcrXmpConverter
import com.cleanlr.storage.DebouncedAutoSaver
import com.cleanlr.storage.LocalDocumentStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Tasks T7, T8, T9: 3-Tier Storage, Debounce Contract & ACR XMP Serialization Test
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SchemaT7StorageAndAcrXmpTest {

    private val sampleRecipe = EditRecipe(
        basic = BasicAdjustments(
            exposure = 1.35f,
            contrast = 35.0f,
            highlights = -45.0f,
            shadows = 20.0f,
            whites = 15.0f,
            blacks = -10.0f,
            temperature = 6200.0f,
            tint = 8.0f,
            vibrance = 25.0f,
            saturation = -5.0f
        ),
        effects = EffectsAdjustments(
            texture = 15.0f,
            clarity = 20.0f,
            dehaze = 10.0f,
            vignetteAmount = -25.0f
        ),
        detail = DetailAdjustments(
            sharpening = 55.0f,
            sharpenRadius = 1.2f,
            sharpenDetail = 30.0f,
            sharpenMasking = 60.0f,
            luminanceNoise = 10.0f,
            colorNoise = 25.0f
        ),
        hsl = HslAdjustments(
            red = HslValues(hue = 5f, saturation = 10f, luminance = -5f),
            blue = HslValues(hue = -10f, saturation = 20f, luminance = 15f)
        ),
        colorGrading = ColorGrading(
            shadows = ColorGradingWheel(hue = 210f, saturation = 30f, luminance = -10f),
            highlights = ColorGradingWheel(hue = 45f, saturation = 25f, luminance = 5f),
            blending = 60f,
            balance = -5f
        )
    )

    @Test
    fun testToXmpGeneratesAuthenticCrsAttributes() {
        val xmp = AcrXmpConverter.toXmp(sampleRecipe)

        assertTrue("Must include crs:Exposure2012", xmp.contains("crs:Exposure2012=\"1.35\""))
        assertTrue("Must include crs:Contrast2012", xmp.contains("crs:Contrast2012=\"35\""))
        assertTrue("Must include crs:Highlights2012", xmp.contains("crs:Highlights2012=\"-45\""))
        assertTrue("Must include crs:Shadows2012", xmp.contains("crs:Shadows2012=\"20\""))
        assertTrue("Must include crs:Texture", xmp.contains("crs:Texture=\"15\""))
        assertTrue("Must include crs:Clarity2012", xmp.contains("crs:Clarity2012=\"20\""))
        assertTrue("Must include crs:Dehaze", xmp.contains("crs:Dehaze=\"10\""))
        assertTrue("Must include crs:Sharpness", xmp.contains("crs:Sharpness=\"55\""))
        assertTrue("Must include crs:HueAdjustmentBlue", xmp.contains("crs:HueAdjustmentBlue=\"-10\""))
        assertTrue("Must include crs:ColorGradeShadowHue", xmp.contains("crs:ColorGradeShadowHue=\"210\""))
    }

    @Test
    fun testFromXmpParsesAuthenticCrsAttributes() {
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description
                  xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
                  crs:Exposure2012="-0.75"
                  crs:Contrast2012="40"
                  crs:Highlights2012="-30"
                  crs:Shadows2012="25"
                  crs:Temperature="6800"
                  crs:Texture="22"
                  crs:Clarity2012="18"
                  crs:Dehaze="12"
                  crs:Sharpness="50"
                  crs:ColorGradeShadowHue="200"
                  crs:ColorGradeShadowSat="35" />
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()

        val parsed = AcrXmpConverter.fromXmp(xmp)

        assertEquals(-0.75f, parsed.basic.exposure, 0.01f)
        assertEquals(40.0f, parsed.basic.contrast, 0.01f)
        assertEquals(-30.0f, parsed.basic.highlights, 0.01f)
        assertEquals(25.0f, parsed.basic.shadows, 0.01f)
        assertEquals(6800.0f, parsed.basic.temperature, 0.01f)
        assertEquals(22.0f, parsed.effects.texture, 0.01f)
        assertEquals(18.0f, parsed.effects.clarity, 0.01f)
        assertEquals(12.0f, parsed.effects.dehaze, 0.01f)
        assertEquals(50.0f, parsed.detail.sharpening, 0.01f)
        assertEquals(200.0f, parsed.colorGrading.shadows.hue, 0.01f)
        assertEquals(35.0f, parsed.colorGrading.shadows.saturation, 0.01f)
    }

    @Test
    fun testXmpRoundTripFidelity() {
        val xmp = AcrXmpConverter.toXmp(sampleRecipe)
        val roundTripped = AcrXmpConverter.fromXmp(xmp)

        assertEquals("Exposure must survive round trip", sampleRecipe.basic.exposure, roundTripped.basic.exposure, 0.02f)
        assertEquals("Contrast must survive round trip", sampleRecipe.basic.contrast, roundTripped.basic.contrast, 1.0f)
        assertEquals("Highlights must survive round trip", sampleRecipe.basic.highlights, roundTripped.basic.highlights, 1.0f)
        assertEquals("Shadows must survive round trip", sampleRecipe.basic.shadows, roundTripped.basic.shadows, 1.0f)
        assertEquals("Texture must survive round trip", sampleRecipe.effects.texture, roundTripped.effects.texture, 1.0f)
        assertEquals("Clarity must survive round trip", sampleRecipe.effects.clarity, roundTripped.effects.clarity, 1.0f)
        assertEquals("Blue hue must survive round trip", sampleRecipe.hsl.blue.hue, roundTripped.hsl.blue.hue, 1.0f)
    }

    @Test
    fun testLocalDocumentStoreSaveAndLoad() {
        val store = LocalDocumentStore()
        val assetId = "test_asset_001"

        assertFalse(store.hasRecipe(assetId))
        val saved = store.saveRecipe(assetId, sampleRecipe)
        assertTrue(saved)
        assertTrue(store.hasRecipe(assetId))

        val loaded = store.loadRecipe(assetId)
        assertNotNull(loaded)
        assertEquals(sampleRecipe.basic.exposure, loaded!!.basic.exposure, 0.001f)
        assertEquals(sampleRecipe.effects.texture, loaded.effects.texture, 0.001f)
    }

    @Test
    fun testDebounceContractCoalescesMultipleSliderTicksIntoSingleWrite() = runTest {
        val store = LocalDocumentStore()
        val autoSaver = DebouncedAutoSaver(store, this, debounceMs = 300L)

        val assetId = "slider_gesture_asset"

        // Simulate 20 rapid slider movement ticks during a drag gesture
        for (i in 1..20) {
            val intermediateRecipe = sampleRecipe.copy(
                basic = sampleRecipe.basic.copy(exposure = i * 0.1f)
            )
            autoSaver.scheduleSave(assetId, intermediateRecipe)
            testScheduler.advanceTimeBy(10) // 10ms between ticks (200ms total)
        }

        // Before 300ms debounce expires, write count should still be 0
        assertEquals("Write count must be 0 while slider is actively dragging", 0, autoSaver.writeCount)

        // Advance past debounce window (300ms)
        testScheduler.advanceTimeBy(350)

        // Exactly ONE durable write must have occurred
        assertEquals("Exactly 1 coalesced write must occur after debounce quiet period", 1, autoSaver.writeCount)

        // Verify saved recipe matches the last tick value (20 * 0.1 = 2.0f)
        val saved = store.loadRecipe(assetId)
        assertNotNull(saved)
        assertEquals(2.0f, saved!!.basic.exposure, 0.01f)
    }

    @Test
    fun testDebouncedAutoSaverFlushWritesImmediately() = runTest {
        val store = LocalDocumentStore()
        val autoSaver = DebouncedAutoSaver(store, this, debounceMs = 300L)

        val assetId = "flush_asset"
        autoSaver.scheduleSave(assetId, sampleRecipe)
        assertEquals(0, autoSaver.writeCount)

        // Call flush (simulating onPause / lifecycle event)
        autoSaver.flush()

        assertEquals("Flush must immediately commit pending write without waiting for debounce", 1, autoSaver.writeCount)
        assertTrue(store.hasRecipe(assetId))
    }
}
