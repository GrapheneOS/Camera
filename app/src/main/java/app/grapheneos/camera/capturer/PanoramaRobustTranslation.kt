package app.grapheneos.camera.capturer

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Deterministic translation RANSAC for consecutive cylindrical, image-based matches.
 * No dependence on gyro sign, frame order or an Android/OpenCV runtime.
 */
internal object PanoramaRobustTranslation {
    data class Displacement(val dx: Double, val dy: Double)
    data class Match(val prevX: Double, val prevY: Double, val nextX: Double, val nextY: Double)
    data class Result(val dx: Double, val dy: Double, val inliers: Int,
                      val candidates: Int, val medianError: Double)

    fun estimate(matches: List<Match>, width: Int, height: Int): Result? {
        if (matches.size < 16 || width <= 0 || height <= 0) return null
        val offsets = matches.map { Displacement(it.prevX - it.nextX, it.prevY - it.nextY) }
        var best = emptyList<Displacement>()
        val seedStep = max(1, offsets.size / 96)
        for (index in offsets.indices step seedStep) {
            val seed = offsets[index]
            if (abs(seed.dx) !in width * 0.018..width * 0.52 ||
                abs(seed.dy) > height * 0.18) continue
            val consensus = offsets.filter {
                abs(it.dx - seed.dx) <= 7.5 && abs(it.dy - seed.dy) <= 9.0
            }
            if (consensus.size > best.size) best = consensus
        }
        if (best.size < 16 || best.size < (matches.size * 0.12).roundToInt()) return null
        val dx = median(best.map { it.dx })
        val dy = median(best.map { it.dy })
        val refined = offsets.filter { abs(it.dx - dx) <= 8 && abs(it.dy - dy) <= 10 }
        if (refined.size < 16) return null
        val mx = median(refined.map { it.dx })
        val my = median(refined.map { it.dy })
        if (abs(mx) !in width * 0.018..width * 0.52 || abs(my) > height * 0.18) return null
        val error = median(refined.map { kotlin.math.hypot(it.dx - mx, it.dy - my) })
        if (error > 7.0) return null
        return Result(mx, my, refined.size, matches.size, error)
    }

    /** The same rule applies to a sequence supplied in either temporal direction. */
    fun reverseOrder(results: List<Result>): Boolean? {
        val accepted = results.filter { it.inliers >= 16 }
        if (accepted.isEmpty()) return null
        val forward = accepted.count { it.dx > 0 }
        val reverse = accepted.count { it.dx < 0 }
        val threshold = max(1, (accepted.size * 0.75).roundToInt())
        return when {
            forward >= threshold -> false
            reverse >= threshold -> true
            else -> null
        }
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    }
}
