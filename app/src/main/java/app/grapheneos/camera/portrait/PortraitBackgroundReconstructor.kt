package app.grapheneos.camera.portrait

import kotlin.math.roundToInt

/**
 * Conservative, background-only color extension for portrait processing.
 *
 * The still-photo compositor has premultiplied RGB and grayscale background
 * coverage (0..255) at the small Gaussian working resolution. Pixels hidden by
 * a subject cannot provide reliable blur samples. Before the existing Gaussian
 * convolutions, extend the color of nearby, confidently visible background
 * into a narrow band behind the foreground silhouette.
 *
 * This does NOT change the original photo, the subject alpha, any Gaussian
 * kernel, or the blur strength map. It is not a replacement for depth-aware
 * inpainting; its finite reach deliberately avoids painting across large
 * foreground areas, including disconnected background components.
 *
 * All operations are integer and bounded. Arrays are modified in place.
 * The same image supplies all seed colors; no external code or native model.
 */
internal object PortraitBackgroundReconstructor {
    private const val CERTAIN_BACKGROUND = 238 // alpha <= ~0.067
    private const val FULL_REPLACEMENT_COVERAGE = 170
    private const val MAX_DISTANCE = 14 // compositor-small-grid pixels; Gaussian radii unchanged

    /** Returns number of pixels actually restored for optional diagnostics. */
    fun extend(
        weightedRgb: IntArray,
        coverageRgb: IntArray,
        width: Int,
        height: Int,
        maxDistance: Int = MAX_DISTANCE
    ): Int {
        require(width > 0 && height > 0 && width <= Int.MAX_VALUE / height)
        val length = width * height
        require(weightedRgb.size == length && coverageRgb.size == length)
        require(maxDistance in 0..64)
        if (maxDistance == 0) return 0

        // Multi-source BFS: every reliable background pixel is a seed. As the
        // queue advances, its seed index propagates only into the small hidden
        // band near the silhouette. Memory and runtime are O(width*height).
        val origin = IntArray(length) { -1 }
        val distance = IntArray(length)
        val queue = IntArray(length)
        var head = 0
        var tail = 0
        for (i in 0 until length) {
            if (coverage(coverageRgb[i]) >= CERTAIN_BACKGROUND) {
                origin[i] = i
                queue[tail++] = i
            }
        }
        if (tail == 0) return 0

        while (head < tail) {
            val index = queue[head++]
            val nextDist = distance[index] + 1
            if (nextDist > maxDistance) continue
            val x = index % width
            val y = index / width
            // Four-connected propagation avoids crossing image boundaries.
            fun visit(other: Int) {
                if (origin[other] != -1) return
                origin[other] = origin[index]
                distance[other] = nextDist
                queue[tail++] = other
            }
            if (x > 0) visit(index - 1)
            if (x + 1 < width) visit(index + 1)
            if (y > 0) visit(index - width)
            if (y + 1 < height) visit(index + width)
        }

        var restored = 0
        for (i in 0 until length) {
            val source = origin[i]
            if (source < 0 || source == i) continue
            val support = coverage(coverageRgb[i])
            if (support >= CERTAIN_BACKGROUND) continue
            val seedSupport = coverage(coverageRgb[source])
            val seed = weightedRgb[source]
            // Convert the seed back from premultiplied RGB; it is reliable
            // because source coverage is near-opaque background.
            val replacementRed = unpremultiply((seed ushr 16) and 255, seedSupport)
            val replacementGreen = unpremultiply((seed ushr 8) and 255, seedSupport)
            val replacementBlue = unpremultiply(seed and 255, seedSupport)
            val original = weightedRgb[i]
            val originalRed = unpremultiply((original ushr 16) and 255, support)
            val originalGreen = unpremultiply((original ushr 8) and 255, support)
            val originalBlue = unpremultiply(original and 255, support)
            // Near a reliable background, retain some original color only if
            // coverage is already high. Deep inside the silhouette use only
            // inferred background, never subject-colored or zero samples.
            val inferredPart = ((CERTAIN_BACKGROUND - support).toFloat() /
                (CERTAIN_BACKGROUND - FULL_REPLACEMENT_COVERAGE)).coerceIn(0f, 1f)
            val r = blend(originalRed, replacementRed, inferredPart)
            val g = blend(originalGreen, replacementGreen, inferredPart)
            val b = blend(originalBlue, replacementBlue, inferredPart)
            weightedRgb[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            coverageRgb[i] = 0xffffffff.toInt()
            restored++
        }
        return restored
    }

    private fun coverage(packed: Int): Int = (packed ushr 16) and 255

    private fun unpremultiply(component: Int, coverage: Int): Int =
        if (coverage <= 0) 0 else ((component * 255f) / coverage).roundToInt().coerceIn(0, 255)

    private fun blend(start: Int, end: Int, part: Float): Int =
        (start + (end - start) * part).roundToInt().coerceIn(0, 255)
}
