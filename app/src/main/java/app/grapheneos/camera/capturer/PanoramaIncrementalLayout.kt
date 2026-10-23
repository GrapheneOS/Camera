package app.grapheneos.camera.capturer

import kotlin.math.abs
import kotlin.math.max

/**
 * Low-resolution preview placement model. It does NOT change the archived JPEGs
 * or provide final image coordinates. All calls are confined to the mosaic executor.
 *
 * Image-verified edges take precedence over yaw-only predictions. A skip-one edge
 * (frame n-2 -> frame n) can revise the two newest, still-provisional placements.
 */
internal class PanoramaIncrementalLayout(private val frameWidth: Int) {
    init { require(frameWidth >= 96) }

    enum class Quality { ANCHOR, MATCHED, SENSOR_ESTIMATE }
    data class Offset(val dx: Float, val dy: Float, val confidence: Float) {
        init { require(dx.isFinite() && dy.isFinite() && confidence in 0f..1f) }
    }
    data class Placement(val heading: Float, val x: Float, val y: Float, val quality: Quality)
    data class Stats(val frames: Int, val matchedEdges: Int, val estimatedEdges: Int,
                     val backtrackRevisions: Int)

    private val placements = ArrayList<Placement>()
    private var matchedEdges = 0
    private var backtrackRevisions = 0

    fun snapshot(): List<Placement> = placements.toList()
    fun stats(): Stats = Stats(placements.size, matchedEdges,
        max(0, placements.size - 1 - matchedEdges), backtrackRevisions)

    /** All offsets are measured in the *same oriented projected thumbnail* pixels. */
    fun append(heading: Float, predictedDx: Float, observed: Offset?): Placement {
        require(heading.isFinite() && predictedDx.isFinite())
        if (placements.isEmpty()) {
            return Placement(heading, 0f, 0f, Quality.ANCHOR).also(placements::add)
        }
        val prev = placements.last()
        // Do not render a repeated/unmoving or wildly separated view as a proven seam.
        val measured = observed?.takeIf {
            it.confidence >= 0.50f &&
                it.dx * predictedDx > 0f &&
                abs(it.dx) in (frameWidth * 0.02f)..(frameWidth * 0.88f) &&
                abs(it.dx - predictedDx) <= max(frameWidth * 0.26f, abs(predictedDx) * 0.80f) &&
                abs(it.dy) <= frameWidth * 0.20f
        }
        if (measured != null) matchedEdges++
        val boundedPrediction = predictedDx.coerceIn(-frameWidth * 0.78f, frameWidth * 0.78f)
        val movementX = measured?.dx ?: boundedPrediction
        val movementY = measured?.dy ?: 0f
        return Placement(heading, prev.x + movementX, prev.y + movementY,
            if (measured != null) Quality.MATCHED else Quality.SENSOR_ESTIMATE)
            .also(placements::add)
    }

    /**
     * Look-ahead: an independently registered n-2 -> n edge can correct the latest
     * provisional frames. Large contradictions are discarded (do not rewrite a sweep).
     */
    fun reviseNewestFromSkip(observed: Offset?): Boolean {
        if (placements.size < 3 || observed == null || observed.confidence < 0.62f) return false
        val n = placements.lastIndex
        val first = placements[n - 2]
        val middle = placements[n - 1]
        val last = placements[n]
        val predicted = last.x - first.x
        if (observed.dx * predicted <= 0f) return false
        val error = observed.dx - predicted
        if (abs(error) < 1f || abs(error) > frameWidth * 0.14f ||
            abs(observed.dy - (last.y - first.y)) > frameWidth * 0.12f) return false
        val midpointX = middle.x + error * 0.5f
        val newLastX = last.x + error
        // Enforce the sweep direction and prevent a zero-width registration.
        if ((midpointX - first.x) * predicted <= 0f ||
            (newLastX - midpointX) * predicted <= 0f) return false
        val verticalError = observed.dy - (last.y - first.y)
        placements[n - 1] = middle.copy(x = midpointX, y = middle.y + verticalError * 0.5f)
        placements[n] = last.copy(x = newLastX, y = last.y + verticalError)
        backtrackRevisions++
        return true
    }

    fun clear() {
        placements.clear(); matchedEdges = 0; backtrackRevisions = 0
    }
}
