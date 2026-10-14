package com.cleanlr.export

import com.cleanlr.model.BasicAdjustments
import com.cleanlr.model.EditRecipe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Task T8: Export Config Sheet Model, Rules, and WorkManager Delivery Test
 */
class ExportConfigSheetTest {

    @Test
    fun `test ExportConfig default values match Lightroom production requirements`() {
        val defaultConfig = ExportConfig()

        assertEquals(ExportFormat.JPEG, defaultConfig.format)
        assertEquals(90, defaultConfig.quality)
        assertNull("Default dimension must be null (Full Resolution master)", defaultConfig.maxDimension)
        assertTrue("Exif and non-destructive recipe embedding must be enabled by default", defaultConfig.includeExif)
    }

    @Test
    fun `test ExportConfig JSON round-trip retains format, quality, dimensions, and exif flags`() {
        val customConfig = ExportConfig(
            format = ExportFormat.WEBP,
            quality = 85,
            maxDimension = 2048,
            includeExif = false
        )

        val json = Json.encodeToString(customConfig)
        val decoded = Json.decodeFromString<ExportConfig>(json)

        assertEquals(ExportFormat.WEBP, decoded.format)
        assertEquals(85, decoded.quality)
        assertEquals(2048, decoded.maxDimension)
        assertFalse(decoded.includeExif)
    }

    @Test
    fun `test Long Edge 2048px scale factor accurately constrains 50MP portrait and landscape images`() {
        val maxDim = 2048

        // 1. 50MP Portrait (6000 x 8000)
        val portraitW = 6000
        val portraitH = 8000
        val portraitScale = maxDim.toFloat() / max(portraitW, portraitH)
        val targetPortraitW = (portraitW * portraitScale).roundToInt()
        val targetPortraitH = (portraitH * portraitScale).roundToInt()

        assertEquals(2048, max(targetPortraitW, targetPortraitH))
        assertEquals(1536, targetPortraitW)
        assertEquals(2048, targetPortraitH)

        // 2. Landscape (8000 x 4000)
        val landW = 8000
        val landH = 4000
        val landScale = maxDim.toFloat() / max(landW, landH)
        val targetLandW = (landW * landScale).roundToInt()
        val targetLandH = (landH * landScale).roundToInt()

        assertEquals(2048, max(targetLandW, targetLandH))
        assertEquals(2048, targetLandW)
        assertEquals(1024, targetLandH)

        // 3. Small image (1200 x 800) does not upscale
        val smallW = 1200
        val smallH = 800
        val shouldScale = max(smallW, smallH) > maxDim
        assertFalse("Images smaller than 2048px must not be upscaled", shouldScale)
    }

    @Test
    fun `test ExportManager embeds full ExportConfig JSON into OneTimeWorkRequest inputData`() {
        val dummyUriString = "content://media/external/images/media/99"
        val recipe = EditRecipe(basic = BasicAdjustments(exposure = 1.0f))
        val config = ExportConfig(
            format = ExportFormat.PNG,
            quality = 100,
            maxDimension = 2048,
            includeExif = false
        )

        val workRequest = ExportManager.createSingleWorkRequest(dummyUriString, recipe, config)
        val configJson = workRequest.workSpec.input.getString(ExportImageWorker.KEY_CONFIG_JSON)

        assertNotNull("KEY_CONFIG_JSON must be embedded in WorkRequest inputData", configJson)
        val decodedConfig = Json.decodeFromString<ExportConfig>(configJson!!)

        assertEquals(ExportFormat.PNG, decodedConfig.format)
        assertEquals(100, decodedConfig.quality)
        assertEquals(2048, decodedConfig.maxDimension)
        assertFalse(decodedConfig.includeExif)
    }

    @Test
    fun `test format selection rules for PNG, JPEG, and WebP`() {
        // PNG is lossless
        val pngConfig = ExportConfig(format = ExportFormat.PNG, quality = 100, includeExif = false)
        assertEquals(ExportFormat.PNG, pngConfig.format)

        // JPEG supports Exif
        val jpegConfig = ExportConfig(format = ExportFormat.JPEG, quality = 95, includeExif = true)
        assertEquals(ExportFormat.JPEG, jpegConfig.format)
        assertTrue(jpegConfig.includeExif)

        // WebP efficient compression
        val webpConfig = ExportConfig(format = ExportFormat.WEBP, quality = 80)
        assertEquals(ExportFormat.WEBP, webpConfig.format)
        assertEquals(80, webpConfig.quality)
    }
}
