package app.grapheneos.camera.portrait

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Conservative relative-depth confidence check for portrait rendering.
 *
 * This is NOT calibrated physical depth or model-supplied confidence. It identifies
 * isolated, depth-inconsistent pixels *only in color-consistent background*
 * and makes bounded corrections to the existing Gaussian layer mixing field.
 * A real RGB boundary is deliberately not diffused across. No foreground alpha,
 * original JPEG pixel, ONNX asset, Gaussian kernel, or blur radius is modified.
 */
internal object PortraitDepthConfidence {
    internal data class Result(
        val field: FloatArray,
        val lowConfidenceCells: Int,
        val adjustedCells: Int,
        val gridWidth: Int,
        val gridHeight: Int
    )

    private const val COLOR_EDGE_LIMIT = 40 // Maximum per-channel RGB distance.
    private const val DEPTH_OUTLIER = .075f
    private const val DEPTH_CONSENSUS = .045f
    private const val MAX_OUTLIER_CORRECTION = .20f
    private const val MAX_SMOOTH_CORRECTION = .025f

    fun stabilize(
        field: FloatArray,
        depth: FloatArray,
        alpha: FloatArray,
        colors: IntArray,
        width: Int,
        height: Int,
        enabled: Boolean = true
    ): Result {
        require(width > 0 && height > 0 && width.toLong() * height <= Int.MAX_VALUE) {
            "Invalid portrait dimensions"
        }
        val count = width * height
        require(field.size == count && depth.size == count && alpha.size == count && colors.size == count) {
            "Portrait confidence inputs must have matching dimensions"
        }
        // Preserve baseline output for flat/failed depth or disabled effects.
        if (!enabled || width < 3 || height < 3) {
            return Result(field, 0, 0, width, height)
        }
        val factor = max(1, (max(width, height) + 383) / 384)
        val gw = (width + factor - 1) / factor
        val gh = (height + factor - 1) / factor
        val length = gw * gh
        val lowDepth = FloatArray(length)
        val lowField = FloatArray(length)
        val lowAlpha = FloatArray(length)
        val lowColor = IntArray(length)
        for (y in 0 until gh) for (x in 0 until gw) {
            val sx = min(width - 1, x * factor + factor / 2)
            val sy = min(height - 1, y * factor + factor / 2)
            val src = sy * width + sx
            val dst = y * gw + x
            lowDepth[dst] = if (depth[src].isFinite()) depth[src].coerceIn(0f, 1f) else Float.NaN
            lowField[dst] = if (field[src].isFinite()) field[src].coerceIn(0f, 1f) else .48f
            lowAlpha[dst] = if (alpha[src].isFinite()) alpha[src].coerceIn(0f, 1f) else 1f
            lowColor[dst] = colors[src]
        }

        val corrections = FloatArray(length)
        var outliers = 0
        var adjustments = 0
        val neighbors = FloatArray(9)
        val strengths = FloatArray(9)
        for (y in 1 until gh - 1) for (x in 1 until gw - 1) {
            val i = y * gw + x
            val centerDepth = lowDepth[i]
            if (lowAlpha[i] > .20f || !centerDepth.isFinite()) continue
            var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val j = (y + dy) * gw + x + dx
                if (lowAlpha[j] > .20f || !lowDepth[j].isFinite()) continue
                if (colorDistance(lowColor[i], lowColor[j]) > COLOR_EDGE_LIMIT) continue
                neighbors[n] = lowDepth[j]
                strengths[n] = lowField[j]
                n++
            }
            // A small color/depth cluster is ambiguous: never invent depth.
            if (n < 6) continue
            neighbors.sort(0, n)
            val medianDepth = neighbors[n / 2]
            var consensus = 0
            for (k in 0 until n) if (abs(neighbors[k] - medianDepth) <= DEPTH_CONSENSUS) consensus++
            val isOutlier = abs(centerDepth - medianDepth) > DEPTH_OUTLIER && consensus * 3 >= n * 2
            val raw = lowField[i]
            var delta = 0f
            if (isOutlier) {
                outliers++
                // The median of all same-colored, background-only layer weights
                // is a robust alternative to an isolated depth error.
                strengths.sort(0, n)
                delta = ((strengths[n / 2] - raw) * .70f)
                    .coerceIn(-MAX_OUTLIER_CORRECTION, MAX_OUTLIER_CORRECTION)
            } else if (abs(centerDepth - medianDepth) < .035f) {
                // Tiny smoothing only across agreement, never a genuine depth edge.
                var total = 0f
                var weight = 0f
                for (dy in -1..1) for (dx in -1..1) {
                    val j = (y + dy) * gw + x + dx
                    if (lowAlpha[j] > .20f || !lowDepth[j].isFinite()) continue
                    val distance = colorDistance(lowColor[i], lowColor[j])
                    if (distance > COLOR_EDGE_LIMIT || abs(lowDepth[j] - centerDepth) > .045f) continue
                    val w = if (dx == 0 && dy == 0) 2f else if (dx == 0 || dy == 0) 1f else .7f
                    total += w * lowField[j]
                    weight += w
                }
                if (weight >= 6f) delta = ((total / weight - raw) * .20f)
                    .coerceIn(-MAX_SMOOTH_CORRECTION, MAX_SMOOTH_CORRECTION)
            }
            corrections[i] = delta
            if (abs(delta) > .00001f) adjustments++
        }
        if (adjustments == 0) return Result(field, outliers, 0, gw, gh)

        val result = field.copyOf()
        for (y in 0 until height) {
            val fy = ((y + .5f) / factor - .5f).coerceIn(0f, (gh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(gh - 1, y0 + 1)
            val ty = fy - y0
            for (x in 0 until width) {
                val i = y * width + x
                val a = alpha[i]
                // Keep ambiguous/visible foreground and fine edges untouched.
                if (!a.isFinite() || a >= .35f) continue
                // Prevent a low-res correction from bleeding into a different
                // colored object after bilinear upscaling of the correction field.
                val sampleX = min(gw - 1, x / factor)
                val sampleY = min(gh - 1, y / factor)
                if (colorDistance(colors[i], lowColor[sampleY * gw + sampleX]) > COLOR_EDGE_LIMIT) continue
                val fx = ((x + .5f) / factor - .5f).coerceIn(0f, (gw - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(gw - 1, x0 + 1)
                val tx = fx - x0
                val v00 = corrections[y0 * gw + x0]
                val v10 = corrections[y0 * gw + x1]
                val v01 = corrections[y1 * gw + x0]
                val v11 = corrections[y1 * gw + x1]
                val top = v00 + (v10 - v00) * tx
                val bottom = v01 + (v11 - v01) * tx
                val change = (top + (bottom - top) * ty) *
                    ((.35f - max(0f, a)) / .35f).coerceIn(0f, 1f)
                val original = if (field[i].isFinite()) field[i].coerceIn(0f, 1f) else .48f
                result[i] = (original + change).coerceIn(0f, 1f)
            }
        }
        return Result(result, outliers, adjustments, gw, gh)
    }

    private fun colorDistance(a: Int, b: Int): Int = max(
        abs((a ushr 16 and 255) - (b ushr 16 and 255)),
        max(
            abs((a ushr 8 and 255) - (b ushr 8 and 255)),
            abs((a and 255) - (b and 255))
        )
    )
}
