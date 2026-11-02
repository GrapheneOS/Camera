package app.grapheneos.camera.capturer

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Feature registration on cylindrically projected, low-resolution luminance images. */
internal object PanoramaAlignment {
    data class GrayFrame(val width: Int, val height: Int, val pixels: ByteArray) {
        init { require(pixels.size == width * height) }
        fun sample(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 255
    }

    data class Offset(val dx: Double, val dy: Double, val inliers: Int, val error: Double)
    data class Point(val x: Int, val y: Int, val descriptor: LongArray, val strength: Double)
    private data class Match(val ax: Int, val ay: Int, val bx: Int, val by: Int, val distance: Int) {
        val dx get() = ax - bx
        val dy get() = ay - by
    }

    private val binaryPairs = IntArray(256 * 4).apply {
        var seed = 0x31D62C3
        for (i in indices) {
            seed = seed xor (seed shl 13)
            seed = seed xor (seed ushr 17)
            seed = seed xor (seed shl 5)
            this[i] = (seed and 0x7fffffff) % 25 - 12
        }
    }

    fun features(input: GrayFrame): List<Point> {
        val image = smooth(input)
        val w = image.width
        val h = image.height
        require(w >= 96 && h >= 96)
        val selected = ArrayList<Point>()
        val cellsX = 10
        val cellsY = 7
        for (cellY in 0 until cellsY) {
            for (cellX in 0 until cellsX) {
                val candidates = ArrayList<Point>()
                val left = max(17, cellX * w / cellsX)
                val right = min(w - 17, (cellX + 1) * w / cellsX)
                val top = max(17, cellY * h / cellsY)
                val bottom = min(h - 17, (cellY + 1) * h / cellsY)
                for (y in top until bottom step 3) {
                    for (x in left until right step 3) {
                        var xx = 0.0
                        var yy = 0.0
                        var xy = 0.0
                        for (oy in -2..2 step 2) {
                            for (ox in -2..2 step 2) {
                                val gx = image.sample(x + ox + 1, y + oy) -
                                    image.sample(x + ox - 1, y + oy)
                                val gy = image.sample(x + ox, y + oy + 1) -
                                    image.sample(x + ox, y + oy - 1)
                                xx += gx * gx
                                yy += gy * gy
                                xy += gx * gy
                            }
                        }
                        val determinant = xx * yy - xy * xy
                        val trace = xx + yy
                        if (trace < 650.0 || determinant <= 0.0) continue
                        val response = determinant / (trace + 1.0)
                        if (response < 35.0) continue
                        candidates.add(Point(x, y, longArrayOf(), response))
                    }
                }
                candidates.sortByDescending { it.strength }
                val local = ArrayList<Point>(7)
                for (candidate in candidates) {
                    if (local.any {
                            abs(it.x - candidate.x) < 10 && abs(it.y - candidate.y) < 10
                        }) continue
                    local.add(candidate.copy(descriptor = describe(image, candidate.x, candidate.y)))
                    if (local.size == 7) break
                }
                selected.addAll(local)
            }
        }
        return selected
    }

    private fun smooth(image: GrayFrame): GrayFrame {
        val w = image.width
        val h = image.height
        val data = image.pixels.clone()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val index = y * w + x
                val sum = image.sample(x - 1, y - 1) + 2 * image.sample(x, y - 1) +
                    image.sample(x + 1, y - 1) + 2 * image.sample(x - 1, y) +
                    4 * image.sample(x, y) + 2 * image.sample(x + 1, y) +
                    image.sample(x - 1, y + 1) + 2 * image.sample(x, y + 1) +
                    image.sample(x + 1, y + 1)
                data[index] = ((sum + 8) / 16).toByte()
            }
        }
        return GrayFrame(w, h, data)
    }

    private fun describe(image: GrayFrame, x: Int, y: Int): LongArray {
        val bits = LongArray(4)
        for (i in 0 until 256) {
            val k = i * 4
            val first = image.sample(x + binaryPairs[k], y + binaryPairs[k + 1])
            val second = image.sample(x + binaryPairs[k + 2], y + binaryPairs[k + 3])
            if (first < second) bits[i / 64] = bits[i / 64] or (1L shl (i % 64))
        }
        return bits
    }

    fun register(
        a: GrayFrame, b: GrayFrame, aPoints: List<Point>, bPoints: List<Point>,
        minShift: Double = 0.025, maxShift: Double = 0.85
    ): Offset? {
        require(a.width == b.width && a.height == b.height)
        val w = a.width
        val h = a.height
        val raw = ArrayList<Match>()
        for (point in aPoints) {
            if (point.x < w * minShift) continue
            var first = 257
            var second = 257
            var best: Point? = null
            for (candidate in bPoints) {
                val dx = point.x - candidate.x
                if (dx < w * minShift || dx > w * maxShift ||
                    abs(point.y - candidate.y) > h * 0.21) continue
                val distance = (0..3).sumOf { bit ->
                    java.lang.Long.bitCount(point.descriptor[bit] xor candidate.descriptor[bit])
                }
                if (distance < first) {
                    second = first
                    first = distance
                    best = candidate
                } else if (distance < second) {
                    second = distance
                }
            }
            if (best != null && first <= 74 && first < second * 0.78) {
                raw.add(Match(point.x, point.y, best.x, best.y, first))
            }
        }
        if (raw.size < 12) return null
        val matches = raw.groupBy { it.bx to it.by }.values.map { group ->
            group.minBy { it.distance }
        }
        if (matches.size < 12) return null

        var best = emptyList<Match>()
        val radius = max(3.0, w * 0.008)
        for (hypothesis in matches) {
            val inliers = matches.filter { other ->
                val dx = (other.dx - hypothesis.dx).toDouble()
                val dy = (other.dy - hypothesis.dy).toDouble()
                dx * dx + dy * dy <= radius * radius
            }
            if (inliers.size > best.size) best = inliers
        }
        if (best.size < 12 || best.size < matches.size * 0.23) return null
        val dx = median(best.map { it.dx.toDouble() })
        val dy = median(best.map { it.dy.toDouble() })
        val residual = best.map { sqrt((it.dx - dx) * (it.dx - dx) + (it.dy - dy) * (it.dy - dy)) }
        val error = median(residual)
        val rangeX = best.maxOf { it.ax } - best.minOf { it.ax }
        val rangeY = best.maxOf { it.ay } - best.minOf { it.ay }
        if (rangeX < w * 0.12 || rangeY < h * 0.22 || error > radius * 0.65) return null
        if (dx < w * minShift || dx > w * maxShift || abs(dy) > h * 0.2) return null
        return Offset(dx, dy, best.size, error)
    }


    /**
     * Content-verified fallback for adjacent frames with too few descriptor matches.
     * The rotation sensor narrows the search; it never supplies an unverified output
     * transform. Gradient agreement and an alternative-offset check reject repeated
     * structures and unrelated views rather than inventing their geometry.
     */
    fun registerGuided(
        a: GrayFrame, b: GrayFrame, expectedDx: Double,
        onRejected: (String) -> Unit = {}
    ): Offset? {
        require(a.width == b.width && a.height == b.height)
        val smoothA = smooth(a)
        val smoothB = smooth(b)
        val w = a.width
        val h = a.height
        if (!expectedDx.isFinite() || expectedDx < w * 0.025 || expectedDx > w * 0.78) {
            onRejected("rotation estimate outside supported range")
            return null
        }
        val allowance = max(w * 0.075, expectedDx * 0.30).toInt()
        val left = max((w * 0.025).toInt(), expectedDx.toInt() - allowance)
        val right = min((w * 0.80).toInt(), expectedDx.toInt() + allowance)
        val maxY = (h * 0.12).toInt()
        var bestX = 0
        var bestY = 0
        var bestScore = Double.POSITIVE_INFINITY
        for (dy in -maxY..maxY step 2) {
            for (dx in left..right step 2) {
                val score = gradientError(smoothA, smoothB, dx, dy, 0, h, 10)
                if (score < bestScore) {
                    bestScore = score
                    bestX = dx
                    bestY = dy
                }
            }
        }
        for (dy in max(-maxY, bestY - 4)..min(maxY, bestY + 4)) {
            for (dx in max(left, bestX - 4)..min(right, bestX + 4)) {
                val score = gradientError(smoothA, smoothB, dx, dy, 0, h, 6)
                if (score < bestScore) {
                    bestScore = score
                    bestX = dx
                    bestY = dy
                }
            }
        }
        // The raw gradient mismatch is close to zero for correctly overlapping
        // images; unrelated indoor views produce substantially larger differences.
        if (bestScore > 39.0 || !bestScore.isFinite()) {
            onRejected("image-gradient mismatch; score=$bestScore")
            return null
        }

        // Check that a clearly different translation is worse. Highly repetitive
        // features (blinds, fences, window grids) must not pass on one good match.
        var competitor = Double.POSITIVE_INFINITY
        for (dx in left..right step 6) {
            if (abs(dx - bestX) < 15) continue
            for (dy in max(-maxY, bestY - 12)..min(maxY, bestY + 12) step 6) {
                competitor = min(competitor, gradientError(smoothA, smoothB, dx, dy, 0, h, 8))
            }
        }
        if (competitor.isFinite() && bestScore >= competitor * 0.91) {
            onRejected("ambiguous image repetition; best=$bestScore alternative=$competitor")
            return null
        }

        // Texture must support the fit throughout at least two thirds of the
        // image. This also excludes a false match on a single moving foreground.
        var agreeingBands = 0
        for (band in 0..2) {
            val y0 = band * h / 3
            val y1 = (band + 1) * h / 3
            val score = gradientError(smoothA, smoothB, bestX, bestY, y0, y1, 5)
            if (score < 43.0) agreeingBands++
        }
        if (agreeingBands < 2) {
            onRejected("insufficient consistent detail across image; bands=$agreeingBands")
            return null
        }
        return Offset(bestX.toDouble(), bestY.toDouble(), agreeingBands * 12,
            bestScore)
    }

    private fun gradientError(
        a: GrayFrame, b: GrayFrame, dx: Int, dy: Int,
        minY: Int, maxY: Int, step: Int
    ): Double {
        val x0 = max(3, dx + 3)
        val x1 = min(a.width - 3, a.width + dx - 3)
        val y0 = max(max(3, dy + 3), minY)
        val y1 = min(min(a.height - 3, a.height + dy - 3), maxY)
        if (x1 <= x0 + 30 || y1 <= y0 + 20) return Double.POSITIVE_INFINITY
        var cost = 0L
        var samples = 0
        for (y in y0 until y1 step step) {
            val by = y - dy
            for (x in x0 until x1 step step) {
                val bx = x - dx
                val axGradient = a.sample(x + 1, y) - a.sample(x - 1, y)
                val ayGradient = a.sample(x, y + 1) - a.sample(x, y - 1)
                if (abs(axGradient) + abs(ayGradient) < 15) continue
                val bxGradient = b.sample(bx + 1, by) - b.sample(bx - 1, by)
                val byGradient = b.sample(bx, by + 1) - b.sample(bx, by - 1)
                cost += min(150, abs(axGradient - bxGradient) + abs(ayGradient - byGradient))
                samples++
            }
        }
        if (samples < 45 || samples < (x1 - x0) * (y1 - y0) / (step * step * 18)) {
            return Double.POSITIVE_INFINITY
        }
        return cost.toDouble() / samples
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2
        else sorted[middle]
    }
}
