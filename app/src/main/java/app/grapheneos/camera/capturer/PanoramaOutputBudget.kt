package app.grapheneos.camera.capturer

import kotlin.math.min
import kotlin.math.sqrt

/** Limits panorama output by available heap and JPEG dimensions, not device model. */
internal object PanoramaOutputBudget {
    const val MAX_JPEG_SIDE = 65_535
    private const val MIN_PIXELS = 36_000_000L
    private const val MAX_PIXELS = 200_000_000L // guard against extreme allocations

    /** ARGB result + one source + its warp + row scratch need spare heap. */
    fun forHeapBytes(maxHeapBytes: Long): Long {
        require(maxHeapBytes > 0L)
        return (maxHeapBytes / 7L).coerceIn(MIN_PIXELS, MAX_PIXELS)
    }

    /** 1.0 means use the full camera-provided pixel dimensions for each frame. */
    fun initialScale(fullWidth: Double, fullHeight: Double, maxPixels: Long): Double {
        require(fullWidth > 0 && fullHeight > 0 &&
            fullWidth.isFinite() && fullHeight.isFinite() && maxPixels > 0L)
        val byMemory = sqrt(maxPixels.toDouble() / (fullWidth * fullHeight))
        val byJpeg = min(MAX_JPEG_SIDE / fullWidth, MAX_JPEG_SIDE / fullHeight)
        return min(1.0, min(byMemory, byJpeg))
    }
}
