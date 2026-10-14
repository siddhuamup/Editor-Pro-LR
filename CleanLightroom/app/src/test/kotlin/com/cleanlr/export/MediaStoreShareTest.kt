package com.cleanlr.export

import android.content.Intent
import android.net.Uri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Task T9: MediaStore Scoped Storage Save & Native Share Sheet Intent Test
 *
 * Verifies Pictures/CleanLightroom directory constraints, MIME type mapping,
 * and Android ACTION_SEND share chooser intent conformance.
 */
class MediaStoreShareTest {

    @Test
    fun `test Scoped Storage album directory matches Pictures CleanLightroom`() {
        assertEquals("CleanLightroom", MediaStoreWriter.ALBUM_DIRECTORY)
        assertEquals("Pictures/CleanLightroom", MediaStoreWriter.RELATIVE_PATH)
    }

    @Test
    fun `test MIME types and file extensions conform for all export formats`() {
        // JPEG
        assertEquals("image/jpeg", MediaStoreWriter.getMimeType(ExportFormat.JPEG))
        assertEquals("jpg", MediaStoreWriter.getExtension(ExportFormat.JPEG))

        // PNG
        assertEquals("image/png", MediaStoreWriter.getMimeType(ExportFormat.PNG))
        assertEquals("png", MediaStoreWriter.getExtension(ExportFormat.PNG))

        // WebP
        assertEquals("image/webp", MediaStoreWriter.getMimeType(ExportFormat.WEBP))
        assertEquals("webp", MediaStoreWriter.getExtension(ExportFormat.WEBP))
    }

    @Test
    fun `test buildShareRequest correctly configures mime type, uri, and read grant`() {
        val dummyUriString = "content://media/external/images/media/777"
        val request = MediaStoreWriter.buildShareRequest(
            imageUriString = dummyUriString,
            format = ExportFormat.JPEG,
            chooserTitle = "Share Clean Lightroom Photo"
        )

        assertEquals(dummyUriString, request.uriString)
        assertEquals("image/jpeg", request.mimeType)
        assertEquals("Share Clean Lightroom Photo", request.chooserTitle)
        assertTrue(request.grantReadPermission)

        // WebP request
        val webpRequest = MediaStoreWriter.buildShareRequest(
            imageUriString = dummyUriString,
            format = ExportFormat.WEBP
        )
        assertEquals("image/webp", webpRequest.mimeType)
    }

    @Test
    fun `test ExportConfig autoShare flag round-trips via serialization`() {
        val configWithShare = ExportConfig(
            format = ExportFormat.JPEG,
            quality = 92,
            autoShare = true
        )

        val json = Json.encodeToString(configWithShare)
        val decoded = Json.decodeFromString<ExportConfig>(json)

        assertTrue("autoShare must be true when serialized and decoded", decoded.autoShare)
        assertEquals(92, decoded.quality)
        assertEquals(ExportFormat.JPEG, decoded.format)
    }
}
