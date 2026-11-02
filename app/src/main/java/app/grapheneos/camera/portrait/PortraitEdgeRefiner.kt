package app.grapheneos.camera.portrait

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Conservative, RGB-guided edge refinement on the still-photo
 * compositor grid. This is not a replacement for a segmentation/matting model.
 *
 * - Fully certain model pixels are preserved, except tiny color-consistent holes.
 * - Semi-transparent edges use local foreground/background RGB evidence to
 *   slightly correct fringe coverage without erasing wispy hair or fur.
 * - All corrections are bounded; no indiscriminate dilation/erosion or blur.
 * - No native, proprietary, or Google portrait code is used.
 *
 * Input pixels and matte must cover the SAME w*h grid, in row-major order.
 */
internal object PortraitEdgeRefiner {
    private const val STRONG_FOREGROUND = .88f
    private const val STRONG_BACKGROUND = .12f
    private const val MAX_EDGE_SHIFT = .09f

    fun refine(matte: FloatArray, argb: IntArray, width: Int, height: Int): FloatArray {
        require(width >= 2 && height >= 2 && matte.size == width * height && argb.size == matte.size)
        val original = FloatArray(matte.size) { index ->
            val a = matte[index]
            if (a.isFinite()) a.coerceIn(0f, 1f) else 0f
        }
        val output = original.copyOf()
        if (width < 3 || height < 3) return output

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val centerAlpha = original[index]
                if (centerAlpha >= .985f) continue // confident real subject stays untouched

                val center = argb[index]
                val r = (center ushr 16) and 255
                val g = (center ushr 8) and 255
                val b = center and 255

                // A single false background pixel surrounded on all four sides
                // by confident, similarly-colored foreground is a likely pinhole.
                // NEVER fill larger holes or geometric gaps (ears/fingers/glasses).
                if (centerAlpha < .55f && original[index - 1] > .94f &&
                    original[index + 1] > .94f && original[index - width] > .94f &&
                    original[index + width] > .94f) {
                    val matchingNeighbors = intArrayOf(index - 1, index + 1, index - width, index + width)
                    var matching = 0
                    for (neighbor in matchingNeighbors) {
                        if (colorDistanceSquared(r, g, b, argb[neighbor]) < 3 * 27 * 27) matching++
                    }
                    if (matching >= 3) {
                        output[index] = max(centerAlpha, .94f)
                        continue
                    }
                }
                if (centerAlpha <= .015f || centerAlpha >= .985f) continue

                // 3x3 bilateral signal: nearby pixels on the OTHER side of an
                // image color edge must contribute less to the new matte.
                var sum = 0f
                var weightSum = 0f
                var closestForeground = Int.MAX_VALUE
                var closestBackground = Int.MAX_VALUE
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val neighbor = index + dy * width + dx
                        val d2 = colorDistanceSquared(r, g, b, argb[neighbor])
                        val proximity = if (dx == 0 && dy == 0) 1f
                            else if (dx == 0 || dy == 0) .75f else .50f
                        // Smooth bounded weight avoids exp() for every pixel.
                        val weight = proximity / (1f + d2 / 2400f)
                        sum += original[neighbor] * weight
                        weightSum += weight
                        if (original[neighbor] >= STRONG_FOREGROUND) {
                            closestForeground = min(closestForeground, d2)
                        }
                        if (original[neighbor] <= STRONG_BACKGROUND) {
                            closestBackground = min(closestBackground, d2)
                        }
                    }
                }
                val bilateral = sum / max(weightSum, 1e-5f)
                // Bounded denoising inside an ambiguous band; never blanket blur.
                var corrected = centerAlpha + ((bilateral - centerAlpha) * .22f)
                    .coerceIn(-.035f, .035f)

                if (closestForeground != Int.MAX_VALUE && closestBackground != Int.MAX_VALUE) {
                    val fg = closestForeground.toFloat()
                    val bg = closestBackground.toFloat()
                    // When subject and background have similar colors, evidence
                    // is weak and the model's original partial alpha dominates.
                    val separation = abs(bg - fg) / (fg + bg + 2200f)
                    val preference = (bg + 100f) / (fg + bg + 200f)
                    val proposal = (preference - centerAlpha) * (.32f * separation)
                    corrected += proposal.coerceIn(-MAX_EDGE_SHIFT, MAX_EDGE_SHIFT)
                }
                // The maximum change applies to the sum of BOTH corrections.
                output[index] = corrected.coerceIn(
                    max(0f, centerAlpha - MAX_EDGE_SHIFT),
                    min(1f, centerAlpha + MAX_EDGE_SHIFT))
            }
        }
        return output
    }

    private fun colorDistanceSquared(red: Int, green: Int, blue: Int, other: Int): Int {
        val dr = red - ((other ushr 16) and 255)
        val dg = green - ((other ushr 8) and 255)
        val db = blue - (other and 255)
        return dr * dr + dg * dg + db * db
    }
}
