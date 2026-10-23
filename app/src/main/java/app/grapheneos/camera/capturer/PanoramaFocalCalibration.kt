package app.grapheneos.camera.capturer

import kotlin.math.hypot

/**
 * Convert EXIF 35 mm-equivalent focal length into focal length in *native source pixels*.
 * The 35 mm still-frame diagonal is sqrt(36^2 + 24^2) = 43.2666 mm. This
 * formulation works for both orientations and 4:3 sensor aspect ratios.
 * EXIF data is optional: an absent/implausible value is not silently treated as calibrated.
 */
internal object PanoramaFocalCalibration {
    private const val FULL_FRAME_DIAGONAL_MM = 43.266615305567875
    /** Approximate fallback for an ordinary ~24-25 mm-eq main camera, NOT calibration. */
    const val UNCERTAIN_FALLBACK_WIDTH_RATIO = 0.70

    fun from35mmEquivalent(nativeWidth: Int, nativeHeight: Int,
                           equivalentMm: Int): Float? {
        if (nativeWidth <= 0 || nativeHeight <= 0 || equivalentMm !in 12..150) return null
        val diagonalPx = hypot(nativeWidth.toDouble(), nativeHeight.toDouble())
        val focalPixels = diagonalPx * equivalentMm / FULL_FRAME_DIAGONAL_MM
        return focalPixels.toFloat().takeIf { it.isFinite() && it > 0f }
    }
}
