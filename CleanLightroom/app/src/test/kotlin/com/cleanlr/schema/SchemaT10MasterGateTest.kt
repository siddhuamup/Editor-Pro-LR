package com.cleanlr.schema

import com.cleanlr.history.HistoryManager
import com.cleanlr.model.*
import com.cleanlr.preset.PresetManager
import com.cleanlr.storage.AcrXmpConverter
import com.cleanlr.storage.LocalDocumentStore
import com.cleanlr.version.VersionManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Task T10: Master Verification Gate & Stress Test (Document 5 Specification)
 *
 * Verifies end-to-end integrity across:
 * 1. Full schema fidelity (PV2012 models)
 * 2. HistoryManager 50-entry bounds under 100-commit stress test
 * 3. 500-point brush stroke memory footprint (< 20KB to prevent OOM)
 * 4. Preset system 0–200% scaling performance under 1,000 rapid iterations (< 100ms)
 * 5. 3-Tier Storage round-trip integration (Runtime -> LocalStore -> ACR XMP -> LocalStore -> Runtime)
 */
class SchemaT10MasterGateTest {

    private val masterRecipe = EditRecipe(
        version = 1,
        assetId = "master_stress_asset",
        isHdrEnabled = false,
        basic = BasicAdjustments(
            exposure = 0.85f,
            contrast = 28.0f,
            highlights = -32.0f,
            shadows = 24.0f,
            whites = 12.0f,
            blacks = -8.0f,
            temperature = 5800.0f,
            tint = 4.0f,
            vibrance = 20.0f,
            saturation = 5.0f
        ),
        effects = EffectsAdjustments(
            texture = 16.0f,
            clarity = 22.0f,
            dehaze = 14.0f,
            vignetteAmount = -20.0f,
            grainAmount = 10.0f
        ),
        detail = DetailAdjustments(
            sharpening = 45.0f,
            sharpenRadius = 1.0f,
            sharpenDetail = 25.0f,
            sharpenMasking = 40.0f,
            luminanceNoise = 5.0f,
            colorNoise = 25.0f
        ),
        curves = ToneCurves.default(),
        hsl = HslAdjustments.default(),
        colorGrading = ColorGrading.default()
    )

    @Test
    fun test1_SchemaFidelityComplete() {
        val json = Json { ignoreUnknownKeys = true }
        val serialized = json.encodeToString(masterRecipe)
        val deserialized = json.decodeFromString<EditRecipe>(serialized)

        assertEquals(masterRecipe.version, deserialized.version)
        assertEquals(masterRecipe.assetId, deserialized.assetId)
        assertEquals(masterRecipe.basic.exposure, deserialized.basic.exposure, 0.001f)
        assertEquals(masterRecipe.effects.texture, deserialized.effects.texture, 0.001f)
        assertEquals(masterRecipe.detail.sharpening, deserialized.detail.sharpening, 0.001f)
    }

    @Test
    fun test2_HistoryManagerStress100CommitsBoundsEnforced() {
        val manager = HistoryManager(masterRecipe)

        // Stress test: 100 rapid commits
        for (i in 1..100) {
            val intermediate = masterRecipe.copy(
                basic = masterRecipe.basic.copy(exposure = (i % 50) * 0.1f)
            )
            manager.commitState(intermediate, "Action $i")
        }

        // Stack size must never exceed MAX_HISTORY_SIZE + 1 (51 entries)
        assertEquals("Undo stack size must be exactly 51 (root + 50)", 51, manager.historyCount.value)

        // Root entry at index 0 must be preserved
        val history = manager.getHistoryDescriptions()
        assertEquals("Root entry must never be evicted", "Original Image", history.first())
        assertEquals("Latest commit must be Action 100", "Action 100", history.last())

        // Verify undo rewinds correctly
        val undone = manager.undo()
        assertNotNull(undone)
        assertEquals(50, manager.historyCount.value)
        assertTrue(manager.canRedo.value)
    }

    @Test
    fun test3_MaskMemoryFootprint500PointsUnder20KB() {
        val points = (1..500).map { i ->
            BrushStrokePoint(
                x = (i * 0.5f) % 1000f,
                y = (i * 0.7f) % 800f,
                radius = 25.0f,
                feather = 30.0f,
                flow = 0.85f
            )
        }

        val brushMask = SubMask.Brush(id = "brush_001", points = points)
        val maskLayer = MaskLayer(id = "layer_001", name = "Subject Adjustment", subMasks = listOf(brushMask))
        val recipeWithMask = masterRecipe.copy(masks = listOf(maskLayer))

        val json = Json { encodeDefaults = true }
        val serialized = json.encodeToString(recipeWithMask)
        val sizeInBytes = serialized.toByteArray(Charsets.UTF_8).size
        val sizeInKB = sizeInBytes / 1024.0

        assertTrue(
            "500-point brush stroke serialized size ($sizeInKB KB) must be < 20KB",
            sizeInKB < 20.0
        )
    }

    @Test
    fun test4_PresetScalingPerformanceBenchmark1000IterationsUnder100ms() {
        val preset = PresetManager.STARTER_PACK.first()

        val elapsed = measureTimeMillis {
            for (i in 1..1000) {
                val amount = (i % 200).toFloat()
                PresetManager.applyPresetWithAmount(masterRecipe, preset, amount)
            }
        }

        assertTrue(
            "1,000 preset scaling calculations must complete under 100ms (took ${elapsed}ms)",
            elapsed < 100
        )
    }

    @Test
    fun test5_FullThreeTierRoundTripIntegration() {
        val store = LocalDocumentStore()
        val assetId = "tier_integration_test_photo"

        // Tier 1 -> Tier 2: Save in-memory recipe to persistent document store
        val saved = store.saveRecipe(assetId, masterRecipe)
        assertTrue("Tier 2 save must succeed", saved)

        val retrievedFromTier2 = store.loadRecipe(assetId)
        assertNotNull("Tier 2 load must succeed", retrievedFromTier2)

        // Tier 2 -> Tier 3: Export to Adobe Camera Raw crs: XMP
        val xmpPacket = AcrXmpConverter.toXmp(retrievedFromTier2!!)
        assertTrue("XMP must contain Camera Raw namespace", xmpPacket.contains("xmlns:crs="))
        assertTrue("XMP must contain Exposure2012", xmpPacket.contains("crs:Exposure2012=\"0.85\""))

        // Tier 3 -> Tier 2 -> Tier 1: Import XMP back and verify complete round-trip fidelity
        val reimportedRecipe = AcrXmpConverter.fromXmp(xmpPacket)
        assertEquals("Exposure must match across 3 tiers", masterRecipe.basic.exposure, reimportedRecipe.basic.exposure, 0.02f)
        assertEquals("Contrast must match across 3 tiers", masterRecipe.basic.contrast, reimportedRecipe.basic.contrast, 1.0f)
        assertEquals("Texture must match across 3 tiers", masterRecipe.effects.texture, reimportedRecipe.effects.texture, 1.0f)
        assertEquals("Sharpening must match across 3 tiers", masterRecipe.detail.sharpening, reimportedRecipe.detail.sharpening, 1.0f)
    }
}
