package com.cleanlr.catalog

import android.net.Uri
import com.cleanlr.ui.editor.FlagState
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Task T2: Catalog Domain Model
 *
 * Represents a photo or RAW asset in the 10,000+ photo catalog.
 */
@Serializable
data class MediaAsset(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val fileName: String,
    val mimeType: String = "image/jpeg",
    val dateTaken: Long = System.currentTimeMillis(),
    val width: Int = 0,
    val height: Int = 0,
    val isRaw: Boolean = false,
    val rating: Int = 0, // 0 to 5 stars
    val flag: FlagState = FlagState.NONE,
    val fileSizeBytes: Long = 0L,
    val hasEdits: Boolean = false
) {
    val uri: Uri get() = Uri.parse(uriString)

    companion object {
        val RAW_EXTENSIONS = setOf("dng", "raw", "arw", "cr2", "cr3", "nef", "orf", "rw2", "pef")
        val RAW_MIME_TYPES = setOf("image/x-adobe-dng", "image/x-sony-arw", "image/x-canon-cr2", "image/x-nikon-nef", "image/dng")

        fun isRawFile(fileName: String, mimeType: String? = null): Boolean {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext in RAW_EXTENSIONS) return true
            if (mimeType != null && mimeType.lowercase() in RAW_MIME_TYPES) return true
            return false
        }
    }
}

/**
 * Query filter for catalog searching and filtering.
 */
data class AssetFilter(
    val query: String = "",
    val rawOnly: Boolean = false,
    val editedOnly: Boolean = false,
    val minRating: Int = 0,
    val flag: FlagState? = null
) {
    fun matches(asset: MediaAsset): Boolean {
        if (query.isNotEmpty() && !asset.fileName.contains(query, ignoreCase = true)) return false
        if (rawOnly && !asset.isRaw) return false
        if (editedOnly && !asset.hasEdits) return false
        if (minRating > 0 && asset.rating < minRating) return false
        if (flag != null && asset.flag != flag) return false
        return true
    }
}

enum class AssetSortOrder {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    NAME_DESC,
    RATING_DESC
}

/**
 * Paged result window for smooth 60 FPS virtualization over 10,000+ assets.
 */
data class PagedResult<T>(
    val items: List<T>,
    val pageIndex: Int,
    val pageSize: Int,
    val totalCount: Int
) {
    val hasNextPage: Boolean get() = (pageIndex + 1) * pageSize < totalCount
    val totalPages: Int get() = if (pageSize > 0) (totalCount + pageSize - 1) / pageSize else 0
}
