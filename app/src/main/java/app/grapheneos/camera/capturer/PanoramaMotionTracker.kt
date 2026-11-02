package app.grapheneos.camera.capturer

import kotlin.math.abs
import kotlin.math.max

/** Low-resolution optical motion estimates used only for live panorama guidance. */
internal class PanoramaMotionTracker(
    private val width: Int = SAMPLE_WIDTH,
    private val height: Int = SAMPLE_HEIGHT
) {
    data class Motion(
        val dx: Float,
        val dy: Float,
        val sharpness: Float,
        val inlierFraction: Float,
        val reliable: Boolean,
        val tracked: Int
    )

    private data class Match(val dx: Int, val dy: Int)
    private var previous: ByteArray? = null

    fun reset() { previous = null }

    /** Samples must already be rotated to the device's upright image orientation. */
    fun track(sample: ByteArray): Motion {
        require(sample.size == width * height)
        val sharpness = sharpness(sample)
        val before = previous
        previous = sample
        if (before == null) return Motion(0f, 0f, sharpness, 0f, false, 0)

        val matches = ArrayList<Match>(12)
        val marginX = max(SEARCH_X + PATCH_RADIUS + 1, width / 6)
        val marginY = max(SEARCH_Y + PATCH_RADIUS + 1, height / 7)
        for (row in 0..3) {
            val y = marginY + row * (height - 2 * marginY) / 3
            for (column in 0..2) {
                val x = marginX + column * (width - 2 * marginX) / 2
                if (localTexture(before, x, y) < MIN_TEXTURE) continue
                var best = Int.MAX_VALUE
                var bestX = 0
                var bestY = 0
                var runnerUp = Int.MAX_VALUE
                for (dy in -SEARCH_Y..SEARCH_Y) {
                    for (dx in -SEARCH_X..SEARCH_X) {
                        val score = patchError(before, sample, x, y, dx, dy)
                        if (score < best) {
                            runnerUp = best
                            best = score
                            bestX = dx
                            bestY = dy
                        } else if (score < runnerUp) {
                            runnerUp = score
                        }
                    }
                }
                // Flat or periodic patches can have many equally plausible matches.
                if (best < MAX_PATCH_ERROR && runnerUp - best >= MIN_MATCH_MARGIN) {
                    matches.add(Match(bestX, bestY))
                }
            }
        }
        if (matches.size < MIN_MATCHES) return Motion(0f, 0f, sharpness, 0f, false, matches.size)
        val sortedX = matches.map { it.dx }.sorted()
        val sortedY = matches.map { it.dy }.sorted()
        val dx = sortedX[sortedX.size / 2]
        val dy = sortedY[sortedY.size / 2]
        val inliers = matches.count { abs(it.dx - dx) <= 2 && abs(it.dy - dy) <= 2 }
        val fraction = inliers.toFloat() / matches.size
        return Motion(dx.toFloat(), dy.toFloat(), sharpness, fraction,
            inliers >= MIN_MATCHES && fraction >= MIN_INLIER_FRACTION, inliers)
    }

    private fun sharpness(frame: ByteArray): Float {
        var sum = 0L
        var count = 0
        for (y in 2 until height - 2 step 2) {
            for (x in 2 until width - 2 step 2) {
                val i = y * width + x
                val center = frame[i].toInt() and 255
                val laplacian = abs(4 * center - (frame[i - 1].toInt() and 255) -
                    (frame[i + 1].toInt() and 255) -
                    (frame[i - width].toInt() and 255) -
                    (frame[i + width].toInt() and 255))
                sum += laplacian
                count++
            }
        }
        return if (count == 0) 0f else sum.toFloat() / count
    }

    private fun localTexture(frame: ByteArray, x: Int, y: Int): Int {
        var texture = 0
        for (py in -PATCH_RADIUS..PATCH_RADIUS step 2) {
            for (px in -PATCH_RADIUS..PATCH_RADIUS step 2) {
                val i = (y + py) * width + x + px
                texture += abs((frame[i].toInt() and 255) - (frame[i + 1].toInt() and 255))
                texture += abs((frame[i].toInt() and 255) - (frame[i + width].toInt() and 255))
            }
        }
        return texture
    }

    private fun patchError(
        previous: ByteArray, current: ByteArray, x: Int, y: Int, dx: Int, dy: Int
    ): Int {
        var error = 0
        for (py in -PATCH_RADIUS..PATCH_RADIUS step 2) {
            for (px in -PATCH_RADIUS..PATCH_RADIUS step 2) {
                val a = previous[(y + py) * width + x + px].toInt() and 255
                val b = current[(y + py + dy) * width + x + px + dx].toInt() and 255
                error += abs(a - b)
            }
        }
        return error
    }

    companion object {
        const val SAMPLE_WIDTH = 96
        const val SAMPLE_HEIGHT = 128
        private const val PATCH_RADIUS = 4
        private const val SEARCH_X = 12
        private const val SEARCH_Y = 9
        private const val MIN_TEXTURE = 120
        private const val MAX_PATCH_ERROR = 1050
        private const val MIN_MATCH_MARGIN = 10
        private const val MIN_MATCHES = 4
        private const val MIN_INLIER_FRACTION = 0.65f
    }
}
