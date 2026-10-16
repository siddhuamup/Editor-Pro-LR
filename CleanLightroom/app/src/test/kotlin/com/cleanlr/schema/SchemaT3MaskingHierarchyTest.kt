package com.cleanlr.schema

import com.cleanlr.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Task T3: MaskLayer & SubMask Sealed Hierarchy Test (Document 5, Phase 5 Foundation)
 *
 * Verifies:
 * 1. MaskLayer container integrity (id, name, enabled, opacity, localAdjustments)
 * 2. Complete 7-type SubMask sealed hierarchy (Brush, LinearGradient, RadialGradient, AISubject, AISky, ColorRange, LuminanceRange)
 * 3. MaskCombineMode support (ADD, SUBTRACT, INTERSECT)
 * 4. Polymorphic JSON serialization & deserialization of all submask types
 * 5. CRITICAL CONTRACT: 500-point brush stroke MUST serialize to < 20KB (< 20,480 bytes)
 * 6. Dual-format deserialization (compact array [x,y,r,f,w] and standard object {"x":...})
 */
class SchemaT3MaskingHierarchyTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `test SubMask sealed hierarchy supports all 7 mask types with combine modes`() {
        val brush = SubMask.Brush(
            id = "mask_brush_1",
            isInverted = false,
            combineMode = MaskCombineMode.ADD,
            points = listOf(BrushStrokePoint(100f, 200f, 25f, 30f, 0.8f))
        )

        val linearGradient = SubMask.LinearGradient(
            id = "mask_lin_1",
            isInverted = true,
            combineMode = MaskCombineMode.SUBTRACT,
            startX = 0.2f, startY = 0.1f,
            endX = 0.8f, endY = 0.9f
        )

        val radialGradient = SubMask.RadialGradient(
            id = "mask_rad_1",
            isInverted = false,
            combineMode = MaskCombineMode.INTERSECT,
            centerX = 0.5f, centerY = 0.5f,
            radiusX = 0.3f, radiusY = 0.2f,
            rotationDegrees = 45f,
            feather = 60f
        )

        val aiSubject = SubMask.AISubject(
            id = "mask_ai_sub",
            isInverted = false,
            combineMode = MaskCombineMode.ADD,
            maskAssetPath = "cache/ai_subject_001.png"
        )

        val aiSky = SubMask.AISky(
            id = "mask_ai_sky",
            isInverted = false,
            combineMode = MaskCombineMode.ADD,
            maskAssetPath = "cache/ai_sky_001.png"
        )

        val colorRange = SubMask.ColorRange(
            id = "mask_color_range",
            isInverted = false,
            combineMode = MaskCombineMode.ADD,
            targetHue = 210f,
            hueTolerance = 25f,
            feather = 15f
        )

        val luminanceRange = SubMask.LuminanceRange(
            id = "mask_lum_range",
            isInverted = true,
            combineMode = MaskCombineMode.INTERSECT,
            minLuminance = 70f,
            maxLuminance = 100f,
            feather = 20f
        )

        val maskLayer = MaskLayer(
            id = "layer_hero_mask",
            name = "Subject Highlights & Sky",
            enabled = true,
            opacity = 0.85f,
            subMasks = listOf(
                brush,
                linearGradient,
                radialGradient,
                aiSubject,
                aiSky,
                colorRange,
                luminanceRange
            ),
            localAdjustments = BasicAdjustments(
                exposure = 0.75f,
                contrast = 20f,
                highlights = -20f
            )
        )

        assertEquals("layer_hero_mask", maskLayer.id)
        assertEquals(7, maskLayer.subMasks.size)
        assertEquals(MaskCombineMode.SUBTRACT, maskLayer.subMasks[1].combineMode)
        assertTrue(maskLayer.subMasks[1].isInverted)
        assertEquals(0.85f, maskLayer.opacity, 1e-4f)
    }

    @Test
    fun `test polymorphic serialization of all submasks inside EditRecipe`() {
        val originalRecipe = EditRecipe(
            assetId = "test_photo_masks",
            masks = listOf(
                MaskLayer(
                    id = "layer_1",
                    name = "Vignette Cutout",
                    opacity = 0.9f,
                    subMasks = listOf(
                        SubMask.RadialGradient(
                            id = "radial_1",
                            centerX = 0.5f,
                            centerY = 0.4f,
                            radiusX = 0.35f,
                            radiusY = 0.25f,
                            combineMode = MaskCombineMode.ADD
                        ),
                        SubMask.Brush(
                            id = "brush_touchup",
                            combineMode = MaskCombineMode.SUBTRACT,
                            points = listOf(
                                BrushStrokePoint(50f, 60f, 15f),
                                BrushStrokePoint(55f, 65f, 15f)
                            )
                        )
                    ),
                    localAdjustments = BasicAdjustments(exposure = -1.0f)
                )
            )
        )

        val encoded = json.encodeToString(EditRecipe.serializer(), originalRecipe)
        assertTrue("Must contain RadialGradient discriminator", encoded.contains("RadialGradient"))
        assertTrue("Must contain Brush discriminator", encoded.contains("Brush"))
        assertTrue("Must contain combineMode SUBTRACT", encoded.contains("SUBTRACT"))

        val decoded = json.decodeFromString(EditRecipe.serializer(), encoded)
        assertEquals(1, decoded.masks.size)
        val decodedLayer = decoded.masks[0]
        assertEquals("layer_1", decodedLayer.id)
        assertEquals(2, decodedLayer.subMasks.size)

        val decodedRadial = decodedLayer.subMasks[0] as SubMask.RadialGradient
        assertEquals(0.5f, decodedRadial.centerX, 1e-4f)
        assertEquals(0.4f, decodedRadial.centerY, 1e-4f)

        val decodedBrush = decodedLayer.subMasks[1] as SubMask.Brush
        assertEquals(MaskCombineMode.SUBTRACT, decodedBrush.combineMode)
        assertEquals(2, decodedBrush.points.size)
        assertEquals(50f, decodedBrush.points[0].x, 1e-4f)
    }

    @Test
    fun `test CRITICAL CONTRACT - 500-point brush stroke MUST serialize to under 20KB`() {
        // Generate a 500-point high-resolution smooth brush stroke
        val points = (1..500).map { i ->
            BrushStrokePoint(
                x = 100.0f + (i * 1.5f),
                y = 200.0f + ((i % 50) * 2.0f),
                radius = 24.5f,
                feather = 30.0f,
                flow = 0.75f
            )
        }

        val brushMask = SubMask.Brush(
            id = "stress_test_500_points",
            points = points
        )

        val recipe = EditRecipe(
            assetId = "brush_stress_test",
            masks = listOf(
                MaskLayer(
                    id = "layer_brush_dense",
                    name = "Dense Hair Mask",
                    subMasks = listOf(brushMask)
                )
            )
        )

        val jsonString = json.encodeToString(EditRecipe.serializer(), recipe)
        val byteCount = jsonString.toByteArray(Charsets.UTF_8).size
        val kbSize = byteCount / 1024.0

        println("500-point brush stroke JSON size: $byteCount bytes ($kbSize KB)")

        // Document 5 Rule: 500-point stroke < 20KB (20,480 bytes)
        assertTrue(
            "500-point brush stroke must serialize to < 20KB to prevent memory bomb (was $kbSize KB)",
            byteCount < 20 * 1024
        )

        // Verify deserialization fidelity of all 500 points
        val decoded = json.decodeFromString(EditRecipe.serializer(), jsonString)
        val decodedBrush = decoded.masks[0].subMasks[0] as SubMask.Brush
        assertEquals(500, decodedBrush.points.size)
        assertEquals(points.first().x, decodedBrush.points.first().x, 1e-4f)
        assertEquals(points.last().y, decodedBrush.points.last().y, 1e-4f)
    }

    @Test
    fun `test dual-format deserialization accepts both compact array and standard object syntax`() {
        // 1. Compact array syntax
        val compactJson = """
            {"id":"b1","isInverted":false,"combineMode":"ADD","points":[[10.0,20.0,15.0,25.0,0.8]]}
        """.trimIndent()
        val decodedCompact = json.decodeFromString(SubMask.Brush.serializer(), compactJson)
        assertEquals(10f, decodedCompact.points[0].x, 1e-4f)
        assertEquals(20f, decodedCompact.points[0].y, 1e-4f)
        assertEquals(15f, decodedCompact.points[0].radius, 1e-4f)
        assertEquals(0.8f, decodedCompact.points[0].flow, 1e-4f)

        // 2. Standard object syntax (legacy or external payload compatibility)
        val objectJson = """
            {"id":"b2","isInverted":false,"combineMode":"ADD","points":[{"x":35.0,"y":45.0,"radius":22.0,"feather":18.0,"flow":1.0}]}
        """.trimIndent()
        val decodedObject = json.decodeFromString(SubMask.Brush.serializer(), objectJson)
        assertEquals(35f, decodedObject.points[0].x, 1e-4f)
        assertEquals(45f, decodedObject.points[0].y, 1e-4f)
        assertEquals(22f, decodedObject.points[0].radius, 1e-4f)
    }
}
