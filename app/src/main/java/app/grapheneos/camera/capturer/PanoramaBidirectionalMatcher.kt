package app.grapheneos.camera.capturer

import kotlin.math.abs
import kotlin.math.max

/**
 * Capture sweep sign is determined from oriented pixels, not IMU sign.
 * A positive dx means next frame moves toward the right in panorama space.
 * The same pair can be presented in either chronological capture direction.
 */
internal object PanoramaBidirectionalMatcher {
    data class Match(val dx: Float, val dy: Float, val confidence: Float,
                     val featuresVerified: Boolean)

    fun find(a: PanoramaAlignment.GrayFrame,
             b: PanoramaAlignment.GrayFrame,
             aPoints: List<PanoramaAlignment.Point>,
             bPoints: List<PanoramaAlignment.Point>,
             predictedMagnitude: Float): Match? {
        val width = a.width.toFloat()
        if (a.width != b.width || a.height != b.height ||
            !predictedMagnitude.isFinite() ||
            predictedMagnitude !in (width * .025f)..(width * .88f)) return null
        val positive = PanoramaAlignment.register(
            a, b, aPoints, bPoints, .025, .88)
        val negative = PanoramaAlignment.register(
            b, a, bPoints, aPoints, .025, .88)
        fun within(o: PanoramaAlignment.Offset?): Boolean =
            o != null && abs(o.dx - predictedMagnitude) <=
                max(width * .28f, predictedMagnitude * 1.05f)
        val forward = positive?.takeIf { within(it) }
        val backward = negative?.takeIf { within(it) }
        if (forward != null || backward != null) {
            // Simultaneous equally credible matches usually mean repeated lines,
            // e.g. sofa edges, blinds or windows. Never arbitrarily pick a sign.
            val winner = when {
                forward != null && backward == null -> forward to 1
                backward != null && forward == null -> backward to -1
                forward != null && backward != null &&
                    forward.inliers >= backward.inliers * 2 -> forward to 1
                forward != null && backward != null &&
                    backward.inliers >= forward.inliers * 2 -> backward to -1
                else -> null
            } ?: return null
            return Match((winner.first.dx * winner.second).toFloat(),
                (winner.first.dy * winner.second).toFloat(), .85f, true)
        }
        // A cautious image-content fallback in BOTH directions. Its lower
        // confidence is explicitly provisional, never evidence of focal intrinsics.
        val aToB = PanoramaAlignment.registerGuided(
            a, b, predictedMagnitude.toDouble())
        val bToA = PanoramaAlignment.registerGuided(
            b, a, predictedMagnitude.toDouble())
        val candidate = when {
            aToB != null && bToA == null -> aToB to 1
            bToA != null && aToB == null -> bToA to -1
            else -> null
        } ?: return null
        return Match((candidate.first.dx * candidate.second).toFloat(),
            (candidate.first.dy * candidate.second).toFloat(), .55f, false)
    }
}
