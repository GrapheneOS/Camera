package app.grapheneos.camera.capturer

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Describes camera movement in a sweep-local frame, not Android's absolute
 * compass/pitch/roll Euler angles. It therefore avoids azimuth gimbal-lock
 * when the device starts substantially inclined (such as while reclining).
 *
 * Rotation matrices are Android sensor device-to-world, row-major 3x3.
 * Does not depend on any Android classes, so it can be tested on the JVM.
 */
internal class PanoramaRelativePose {
    data class Pose(
        /** Wrapped horizontal sweep angle relative to the first sample, radians. */
        val heading: Float,
        /** Vertical deviation of the camera optical axis from the original sweep plane. */
        val vertical: Float,
        /** Twist of the frame about its optical axis, relative to the start. */
        val bank: Float,
        /** False when the optical axis is almost normal to the original sweep plane. */
        val reliable: Boolean
    )

    private var reference: FloatArray? = null
    private var upX = 0f
    private var upY = 1f
    private var gravityBasis = false

    fun reset() {
        reference = null
        upX = 0f
        upY = 1f
        gravityBasis = false
    }

    fun update(matrix: FloatArray): Pose {
        require(matrix.size >= 9) { "Expected 3x3 rotation matrix" }
        var initial = reference
        if (initial == null) {
            initial = matrix.copyOf(9)
            reference = initial
            // World gravity-up expressed in the starting phone's image plane.
            // For a near-ceiling-facing camera this projection can vanish;
            // keep a stable device-up basis for the entire sweep in that case.
            val gx = initial[6]
            val gy = initial[7]
            val projectionLength = hypot(gx, gy)
            if (projectionLength >= 0.25f) {
                upX = gx / projectionLength
                upY = gy / projectionLength
                gravityBasis = true
            }
            return Pose(0f, 0f, 0f, true)
        }

        // Relative transform: R_start^T * R_current.
        val rel = FloatArray(9)
        for (row in 0..2) {
            for (col in 0..2) {
                var value = 0f
                for (k in 0..2) value += initial[k * 3 + row] * matrix[k * 3 + col]
                rel[row * 3 + col] = value
            }
        }

        // Camera points along local -Z. Its horizontal-right and image-up
        // axes are pinned to the first shot, not to world azimuth.
        val rightX = upY
        val rightY = -upX
        val forwardX = -rel[2]
        val forwardY = -rel[5]
        val forwardZ = -rel[8]
        val horizontalRight = forwardX * rightX + forwardY * rightY
        val verticalUp = forwardX * upX + forwardY * upY
        val heading = atan2(-horizontalRight, -forwardZ)
        val vertical = asin(verticalUp.coerceIn(-1f, 1f))

        // Measure rotation of the image's up direction within the original
        // image plane. A perfect horizontal sweep about up leaves bank at 0.
        val currentUpX = rel[0] * upX + rel[1] * upY
        val currentUpY = rel[3] * upX + rel[4] * upY
        val frameRight = currentUpX * rightX + currentUpY * rightY
        val frameUp = currentUpX * upX + currentUpY * upY
        val bank = atan2(-frameRight, frameUp)
        val horizontalLength = sqrt((horizontalRight * horizontalRight + forwardZ * forwardZ).coerceAtLeast(0f))
        if (gravityBasis) {
            // Measure horizontal rotation around gravity, including when the camera is tilted.
            val x0 = -initial[2]
            val y0 = -initial[5]
            val x1 = -matrix[2]
            val y1 = -matrix[5]
            val h0 = hypot(x0, y0)
            val h1 = hypot(x1, y1)
            val worldHeading = atan2(x0 * y1 - y0 * x1, x0 * x1 + y0 * y1)
            val worldVertical = asin((-matrix[8]).coerceIn(-1f, 1f)) -
                asin((-initial[8]).coerceIn(-1f, 1f))
            // Gravity's projection into each camera image reveals actual
            // banking of the horizon, independent of horizontal yaw.
            val nowUx = matrix[6]
            val nowUy = matrix[7]
            val nowLen = hypot(nowUx, nowUy)
            val ux = if (nowLen >= 0.001f) nowUx / nowLen else 0f
            val uy = if (nowLen >= 0.001f) nowUy / nowLen else 0f
            val worldBank = atan2(ux * upY - uy * upX, ux * upX + uy * upY)
            return Pose(worldHeading, worldVertical, worldBank,
                h0 >= 0.25f && h1 >= 0.20f && nowLen >= 0.20f)
        }
        // Camera initially faces near-vertically. World bearing and horizon
        // have a singularity, so use a stable locally defined sweep plane.
        return Pose(heading, vertical, bank, horizontalLength >= 0.40f)
    }
}
