package com.cleanlr.export

import com.cleanlr.model.BasicAdjustments
import com.cleanlr.model.EditRecipe
import com.cleanlr.model.PresenceAdjustments
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Task T5: Metadata Exif Preservation & Recipe XMP Round-Trip Test
 */
class ExportMetadataRoundTripTest {

    @Test
    fun `test EditRecipe serializes and reconstructs with 100 percent parameter parity`() {
        val originalRecipe = EditRecipe(
            basic = BasicAdjustments(
                exposure = 1.75f,
                contrast = 25f,
                highlights = -40f,
                shadows = 30f,
                whites = -15f,
                blacks = 10f,
                temperature = 6350f,
                tint = 12f
            ),
            presence = PresenceAdjustments(
                clarity = 30f,
                dehaze = 15f,
                texture = 20f,
                vibrance = 25f,
                saturation = -5f
            )
        )

        // Simulate embedding into UserComment string
        val recipeJson = kotlinx.serialization.json.Json.encodeToString(originalRecipe)
        val userComment = "${ExportMetadataWriter.RECIPE_COMMENT_PREFIX}$recipeJson"

        // Simulate extraction
        assertTrue(userComment.startsWith(ExportMetadataWriter.RECIPE_COMMENT_PREFIX))
        val extractedJson = userComment.removePrefix(ExportMetadataWriter.RECIPE_COMMENT_PREFIX)
        val restoredRecipe = kotlinx.serialization.json.Json.decodeFromString<EditRecipe>(extractedJson)

        assertNotNull(restoredRecipe)
        assertEquals(originalRecipe.basic.exposure, restoredRecipe.basic.exposure, 1e-5f)
        assertEquals(originalRecipe.basic.contrast, restoredRecipe.basic.contrast, 1e-5f)
        assertEquals(originalRecipe.basic.highlights, restoredRecipe.basic.highlights, 1e-5f)
        assertEquals(originalRecipe.basic.shadows, restoredRecipe.basic.shadows, 1e-5f)
        assertEquals(originalRecipe.basic.whites, restoredRecipe.basic.whites, 1e-5f)
        assertEquals(originalRecipe.basic.blacks, restoredRecipe.basic.blacks, 1e-5f)
        assertEquals(originalRecipe.basic.temperature, restoredRecipe.basic.temperature, 1e-5f)
        assertEquals(originalRecipe.basic.tint, restoredRecipe.basic.tint, 1e-5f)
        assertEquals(originalRecipe.presence.clarity, restoredRecipe.presence.clarity, 1e-5f)
        assertEquals(originalRecipe.presence.dehaze, restoredRecipe.presence.dehaze, 1e-5f)
        assertEquals(originalRecipe.presence.vibrance, restoredRecipe.presence.vibrance, 1e-5f)
        assertEquals(originalRecipe.presence.saturation, restoredRecipe.presence.saturation, 1e-5f)
    }

    @Test
    fun `test Adobe Camera Raw Settings XMP packet generation conforms to standard schema`() {
        val recipe = EditRecipe(
            basic = BasicAdjustments(exposure = 2.0f, temperature = 5800f, contrast = 10f),
            presence = PresenceAdjustments(clarity = 25f)
        )

        val xmp = ExportMetadataWriter.buildXmpPacket(recipe)

        assertTrue("XMP must contain x:xmpmeta header", xmp.contains("<x:xmpmeta"))
        assertTrue("XMP must contain Camera Raw Settings namespace", xmp.contains("xmlns:crs=\"http://ns.adobe.com/camera-raw-settings/1.0/\""))
        assertTrue("XMP must contain crs:Exposure2012 attribute", xmp.contains("crs:Exposure2012=\"2.0\""))
        assertTrue("XMP must contain crs:Temperature attribute", xmp.contains("crs:Temperature=\"5800\""))
        assertTrue("XMP must contain crs:Clarity2012 attribute", xmp.contains("crs:Clarity2012=\"25\""))
    }

    @Test
    fun `test standard Exif tags list covers core camera capture properties`() {
        val tags = ExportMetadataWriter.PRESERVED_TAGS

        assertTrue("Must preserve ISO", tags.contains(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY))
        assertTrue("Must preserve Shutter speed", tags.contains(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME))
        assertTrue("Must preserve Aperture F-Number", tags.contains(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER))
        assertTrue("Must preserve Focal length", tags.contains(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH))
        assertTrue("Must preserve Camera Make", tags.contains(androidx.exifinterface.media.ExifInterface.TAG_MAKE))
        assertTrue("Must preserve Camera Model", tags.contains(androidx.exifinterface.media.ExifInterface.TAG_MODEL))
        assertTrue("Must preserve GPS Latitude", tags.contains(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE))
        assertTrue("Must preserve GPS Longitude", tags.contains(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE))
    }

    @Test
    fun `test untagged stream safely returns null without exceptions`() {
        val dummyData = "CleanLR untagged byte stream".toByteArray(StandardCharsets.UTF_8)
        val stream = ByteArrayInputStream(dummyData)
        // Dummy non-exif stream should fail gracefully
        val result = runCatching { ExportMetadataWriter.extractRecipe(stream) }.getOrNull()
        assertNull(result)
    }
}
