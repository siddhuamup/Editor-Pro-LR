package com.cleanlr.export

import androidx.exifinterface.media.ExifInterface
import com.cleanlr.model.EditRecipe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream

/**
 * Task T5: ExportMetadataWriter
 * 1. Preserves original camera Exif metadata (ISO, ExposureTime, FNumber, FocalLength, Model, GPS).
 * 2. Injects non-destructive EditRecipe into XMP & UserComment so exported photos can be re-imported
 *    with develop settings and history restored.
 */
object ExportMetadataWriter {

    const val RECIPE_COMMENT_PREFIX = "CLEANLR_RECIPE:"
    const val SOFTWARE_TAG = "Clean Lightroom 1.0 (Adobe Lightroom Compatible)"

    // Standard camera capture Exif tags to preserve from source
    val PRESERVED_TAGS = listOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF
    )

    /**
     * Injects camera Exif metadata from source and embeds EditRecipe for non-destructive round-trip.
     */
    fun injectMetadata(
        sourceExif: ExifInterface?,
        targetFile: File,
        recipe: EditRecipe
    ) {
        val targetExif = ExifInterface(targetFile.absolutePath)
        copyExifTags(sourceExif, targetExif)
        embedRecipe(targetExif, recipe)
        targetExif.saveAttributes()
    }

    /**
     * Copies all preserved Exif metadata tags from source to target.
     */
    fun copyExifTags(source: ExifInterface?, target: ExifInterface) {
        if (source != null) {
            for (tag in PRESERVED_TAGS) {
                val value = source.getAttribute(tag)
                if (value != null) {
                    target.setAttribute(tag, value)
                }
            }
        }
        target.setAttribute(ExifInterface.TAG_SOFTWARE, SOFTWARE_TAG)
    }

    /**
     * Embeds the EditRecipe into standard metadata fields.
     */
    fun embedRecipe(target: ExifInterface, recipe: EditRecipe) {
        val recipeJson = Json.encodeToString(recipe)
        // 1. Universal UserComment tag
        target.setAttribute(ExifInterface.TAG_USER_COMMENT, "$RECIPE_COMMENT_PREFIX$recipeJson")
        // 2. Also populate ImageDescription for compatibility
        target.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "$RECIPE_COMMENT_PREFIX$recipeJson")
        // 3. Adobe XMP packet
        val xmpPacket = buildXmpPacket(recipe)
        try {
            target.setAttribute(ExifInterface.TAG_XMP, xmpPacket)
        } catch (_: Exception) {
            // Some older formats might not accept direct TAG_XMP, UserComment provides 100% fallback
        }
    }

    /**
     * Extracts an embedded EditRecipe from an image file if present.
     */
    fun extractRecipe(file: File): EditRecipe? {
        val exif = ExifInterface(file.absolutePath)
        return extractRecipe(exif)
    }

    /**
     * Extracts an embedded EditRecipe from an InputStream if present.
     */
    fun extractRecipe(inputStream: InputStream): EditRecipe? {
        val exif = ExifInterface(inputStream)
        return extractRecipe(exif)
    }

    /**
     * Extracts an embedded EditRecipe from an ExifInterface instance.
     */
    fun extractRecipe(exif: ExifInterface): EditRecipe? {
        val comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
        if (comment != null && comment.startsWith(RECIPE_COMMENT_PREFIX)) {
            val json = comment.removePrefix(RECIPE_COMMENT_PREFIX)
            return runCatching { Json.decodeFromString<EditRecipe>(json) }.getOrNull()
        }

        val desc = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
        if (desc != null && desc.startsWith(RECIPE_COMMENT_PREFIX)) {
            val json = desc.removePrefix(RECIPE_COMMENT_PREFIX)
            return runCatching { Json.decodeFromString<EditRecipe>(json) }.getOrNull()
        }

        return null
    }

    /**
     * Builds an authentic Adobe Camera Raw Settings (CRS) XMP packet.
     */
    fun buildXmpPacket(recipe: EditRecipe): String {
        return """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                  xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
                  crs:Exposure2012="${recipe.basic.exposure}"
                  crs:Contrast2012="${recipe.basic.contrast.toInt()}"
                  crs:Highlights2012="${recipe.basic.highlights.toInt()}"
                  crs:Shadows2012="${recipe.basic.shadows.toInt()}"
                  crs:Whites2012="${recipe.basic.whites.toInt()}"
                  crs:Blacks2012="${recipe.basic.blacks.toInt()}"
                  crs:Temperature="${recipe.basic.temperature.toInt()}"
                  crs:Tint="${recipe.basic.tint.toInt()}"
                  crs:Clarity2012="${recipe.presence.clarity.toInt()}"
                  crs:Dehaze="${recipe.presence.dehaze.toInt()}"
                  crs:Vibrance="${recipe.presence.vibrance.toInt()}"
                  crs:Saturation="${recipe.presence.saturation.toInt()}">
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()
    }
}
