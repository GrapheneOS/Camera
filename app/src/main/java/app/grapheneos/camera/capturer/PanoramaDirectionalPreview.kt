package app.grapheneos.camera.capturer

import kotlin.math.abs
import kotlin.math.max

/** The preview tracks direction from the image, while IMU predicts distance. */
internal class PanoramaDirectionalPreview(private val width: Int) {
    init { require(width >= 96) }
    private var voteSign = 0
    private var consecutiveVotes = 0
    private var lockedSign = 0
    data class Decision(val predictedDx: Float, val observedDx: Float?,
                        val signVerified: Boolean)

    fun update(sensorDeltaPixels: Float, rotationDegrees: Int,
               imageDx: Float?, imageVerified: Boolean): Decision {
        require(sensorDeltaPixels.isFinite())
        val magnitude = abs(sensorDeltaPixels).coerceAtMost(width * .78f)
        // A feature-confirmed visual direction overrides the sign of device yaw.
        // A single match can guide the preview; >=2 consistent matches lock it.
        val validVisual = imageDx?.takeIf {
            it.isFinite() && abs(it) in (width * .025f)..(width * .88f) &&
                abs(abs(it) - magnitude) <= max(width * .28f, magnitude * 1.05f)
        }
        val sign = if (validVisual != null && validVisual > 0f) 1 else -1
        if (validVisual != null && imageVerified && (lockedSign == 0 || lockedSign == sign)) {
            if (sign == voteSign) consecutiveVotes++
            else { voteSign = sign; consecutiveVotes = 1 }
            if (consecutiveVotes >= 2) lockedSign = sign
        }
        val accepted = validVisual?.takeIf { lockedSign == 0 || sign == lockedSign }
        val provisionalSign = if (accepted != null) {
            if (accepted > 0f) 1 else -1
        } else if (lockedSign != 0) lockedSign
        else if ((rotationDegrees % 360 + 360) % 360 == 90) {
            // Unverified fallback only. Portrait JPEG coordinates on this Pixel
            // have reversed sign relative to raw sensor yaw. Optical overrides it.
            if (sensorDeltaPixels <= 0f) 1 else -1
        } else if (sensorDeltaPixels >= 0f) 1 else -1
        return Decision(provisionalSign * magnitude,
            accepted, lockedSign != 0)
    }
    fun reset() { voteSign = 0; consecutiveVotes = 0; lockedSign = 0 }
}
