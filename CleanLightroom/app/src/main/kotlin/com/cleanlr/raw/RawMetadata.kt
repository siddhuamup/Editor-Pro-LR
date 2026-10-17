package com.cleanlr.raw

/**
 * Task T3: RAW Image Metadata and Camera Color Matrix (Adobe DNG Specification)
 */
data class RawMetadata(
    val make: String = "Generic",
    val model: String = "Digital Camera",
    val width: Int,
    val height: Int,
    val blackLevel: Int = 512,
    val whiteLevel: Int = 16383, // 14-bit ADC default
    val asShotNeutral: FloatArray = floatArrayOf(0.48f, 1.0f, 0.62f), // R, G, B gains
    val colorMatrix1: FloatArray = ColorMatrices.D65_SRGB, // 3x3 XYZ to Camera Native
    val iso: Int = 100,
    val exposureTimeSeconds: Float = 0.01f,
    val fNumber: Float = 2.8f,
    val isDng: Boolean = true
) {
    val bitDepth: Int = 14
    val megapixelCount: Double get() = (width.toDouble() * height.toDouble()) / 1_000_000.0

    /**
     * Calculates the uncompressed 16-bit RGBA memory requirement in Megabytes (MB).
     */
    val uncompressed16BitMb: Double get() = (width.toLong() * height.toLong() * 8L) / (1024.0 * 1024.0)
}

object ColorMatrices {
    // Standard D65 sRGB to XYZ reference matrix
    val D65_SRGB = floatArrayOf(
        0.4124564f, 0.3575761f, 0.1804375f,
        0.2126729f, 0.7151522f, 0.0721750f,
        0.0193339f, 0.1191920f, 0.9503041f
    )

    // Standard Melissa / ProPhoto RGB reference matrix
    val PROPHOTO_RGB = floatArrayOf(
        0.7976749f, 0.1351917f, 0.0313534f,
        0.2880402f, 0.7118741f, 0.0000857f,
        0.0000000f, 0.0000000f, 0.8252100f
    )
}
