package app.grapheneos.camera.capturer

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Presentation-only panorama progress. Capture / registration keep signed camera
 * headings; the user's progress bar is *always* left-to-right, with no backwards
 * motion caused by the gyro drifting or the user briefly correcting their aim.
 *
 * Wait for a confirmed capture direction before showing progress: otherwise the
 * first few noisy sensor samples may flip the progress indicator end to end.
 */
internal class PanoramaDisplayProgress {
    data class State(val fraction: Float, val degrees: Int)

    private var farthestRadians = 0f

    fun update(firstHeading: Float?, currentHeading: Float, direction: Int,
               targetRadians: Float): State {
        if (firstHeading == null || !firstHeading.isFinite() ||
            !currentHeading.isFinite() || !targetRadians.isFinite() ||
            targetRadians <= 0f || (direction != -1 && direction != 1)) {
            return snapshot(targetRadians)
        }
        val forward = (currentHeading - firstHeading) * direction
        // Only committed positive travel may advance the indicator. Reversals,
        // wraps of tiny sensor noise and transient opposite-heading values freeze it.
        if (forward.isFinite()) farthestRadians = max(farthestRadians, forward)
        return snapshot(targetRadians)
    }

    fun reset() { farthestRadians = 0f }

    private fun snapshot(targetRadians: Float): State {
        if (!targetRadians.isFinite() || targetRadians <= 0f) return State(0f, 0)
        val shown = farthestRadians.coerceIn(0f, targetRadians)
        return State(shown / targetRadians, Math.toDegrees(shown.toDouble()).roundToInt())
    }
}
