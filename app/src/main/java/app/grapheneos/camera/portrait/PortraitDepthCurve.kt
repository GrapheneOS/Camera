package app.grapheneos.camera.portrait

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Independent, bounded relative-depth-to-blur mapping for portrait rendering.
 *
 * Depth Anything V2 returns relative depth, not calibrated distance/disparity.
 * This lookup table expresses a *signed* amount of defocus around a robust
 * subject-depth focus band; it does not claim to reproduce Google's Gouda LUT.
 * The caller keeps the existing Gaussian blur layers and original subject matte.
 *
 * The entire class is Android-free so the numeric contract can be checked by
 * the same arithmetic to be validated independently of Android.
 */
internal class PortraitDepthCurve private constructor(
    val focus: Float,
    val rearFocusHalfWidth: Float,
    val frontFocusHalfWidth: Float,
    val rearScale: Float,
    val fallbackStrength: Float,
    private val signedLookup: FloatArray
) {
    /** Magnitude used to blend the existing three Gaussian layers. */
    fun blurStrength(relativeNearDepth: Float): Float {
        if (fallbackStrength >= 0f) return fallbackStrength
        if (!relativeNearDepth.isFinite()) return 0f
        val scaled = relativeNearDepth.coerceIn(0f, 1f) * (LOOKUP_SIZE - 1)
        val low = scaled.toInt().coerceIn(0, LOOKUP_SIZE - 1)
        val high = min(LOOKUP_SIZE - 1, low + 1)
        val interpolated = signedLookup[low] +
            (signedLookup[high] - signedLookup[low]) * (scaled - low)
        return abs(interpolated).coerceIn(0f, 1f)
    }

    companion object {
        private const val LOOKUP_SIZE = 256
        // Conservative focus band: enough to smooth subject-depth noise without
        // defocusing the current portrait's near-background appearance.
        private const val MIN_FOCUS_HALF_WIDTH = .004f
        private const val MAX_FOCUS_HALF_WIDTH = .028f

        fun fromSamples(subject: List<Float>, background: List<Float>): PortraitDepthCurve {
            val goodSubject = subject.filter { it.isFinite() && it in 0f..1f }
            val goodBackground = background.filter { it.isFinite() && it in 0f..1f }
            val focus = percentile(goodSubject, .5f, .5f)

            // Keep the established single-camera fallback when background depth
            // is flat/invalid. This is essential for poor-depth or pet captures.
            val positive = goodBackground.map { max(0f, focus - it) }.filter { it > .003f }
            val fallback = when {
                goodBackground.size < 100 -> .10f
                positive.size < 100 || positive.size.toFloat() / goodBackground.size < .12f -> .48f
                else -> -1f
            }
            val scale = max(.05f, percentile(positive, .82f, .18f))
            val spread = (percentile(goodSubject, .90f, focus) -
                percentile(goodSubject, .10f, focus)).coerceAtLeast(0f)
            val halfBand = (.004f + spread * .14f)
                .coerceIn(MIN_FOCUS_HALF_WIDTH, MAX_FOCUS_HALF_WIDTH)
            val rearBand = halfBand
            val frontBand = min(MAX_FOCUS_HALF_WIDTH, halfBand * 1.15f)
            val lookup = FloatArray(LOOKUP_SIZE) { index ->
                val depth = index.toFloat() / (LOOKUP_SIZE - 1)
                val rear = (focus - rearBand - depth).coerceAtLeast(0f)
                val front = (depth - focus - frontBand).coerceAtLeast(0f)
                when {
                    rear > 0f -> -(1f - exp(-1.30f * rear / scale)).coerceIn(0f, 1f)
                    front > 0f -> (1f - exp(-.88f * front / scale)).coerceIn(0f, 1f)
                    else -> 0f
                }
            }
            return PortraitDepthCurve(focus, rearBand, frontBand, scale, fallback, lookup)
        }

        private fun percentile(values: List<Float>, fraction: Float, fallback: Float): Float {
            if (values.isEmpty()) return fallback
            val sorted = values.sorted()
            return sorted[((sorted.size - 1) * fraction.coerceIn(0f, 1f)).roundToInt()]
        }
    }
}
