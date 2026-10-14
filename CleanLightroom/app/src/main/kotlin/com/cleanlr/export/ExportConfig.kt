package com.cleanlr.export

import kotlinx.serialization.Serializable

/**
 * Supported export image formats.
 */
@Serializable
enum class ExportFormat {
    JPEG,
    PNG,
    WEBP
}

/**
 * Export configuration defining output format, compression quality, dimension constraints,
 * and metadata preservation.
 *
 * @param format Output encoding format (JPEG, PNG, WEBP)
 * @param quality Compression quality (1 to 100, applied to lossy formats like JPEG/WebP)
 * @param maxDimension Optional max long-edge dimension in pixels (e.g. 2048 for web export, null for full resolution)
 * @param includeExif Whether to preserve source camera Exif metadata and embed EditRecipe XMP
 */
@Serializable
data class ExportConfig(
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 90,
    val maxDimension: Int? = null,
    val includeExif: Boolean = true,
    val autoShare: Boolean = false
)
