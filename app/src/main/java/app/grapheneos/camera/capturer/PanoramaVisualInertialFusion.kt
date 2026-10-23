package app.grapheneos.camera.capturer

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Conservative online visual and inertial motion estimates.
 *
 * Camera timestamps and SensorEvent timestamps MUST agree before IMU data is trusted.
 * An optical match is a displacement of the 96x128 analysis preview, NOT a calibrated
 * angular pose. The signed pixel/radian ratio is learned from genuinely matched pairs;
 * no hard-coded focal length, invented certainty, or accelerometer dead reckoning.
 *
 * The estimator runs on the analysis executor only. A failed / missing IMU track does
 * not interrupt JPEG capture. Outputs remain advisory until tested on actual devices.
 */
internal class PanoramaVisualInertialFusion {
    enum class Source { INITIAL, FUSED, VISUAL_ONLY, IMU_ESTIMATED, CONFLICT, UNTRACKED }

    data class Estimate(
        val timestampNs: Long,
        val source: Source,
        /** Estimated displacement since the previous analyzed frame, in preview pixels. */
        val dx: Float,
        val dy: Float,
        val confidence: Float,
        val synchronizedClocks: Boolean,
        val headingDeltaRadians: Float?,
        val totalSensorHeadingRadians: Float?,
        val calibrationPixelsPerRadian: Float?,
        val rawGyroSpeedRadPerSecond: Float?,
        val reasons: String
    )

    private val relativePose = PanoramaRelativePose()
    private var previousFrameTimestampNs: Long? = null
    private var previousReceiveTimestampNs: Long? = null
    private var previousFrameHeading: Float? = null
    private var previousPreviewRotation: Int? = null
    private var sensorHeadingTotal = 0f
    private val scaleObservations = ArrayDeque<Float>()
    private val estimateHistory = ArrayDeque<Estimate>()
    private var clockAgreementStreak = 0

    fun reset() {
        relativePose.reset()
        previousFrameTimestampNs = null
        previousReceiveTimestampNs = null
        previousFrameHeading = null
        previousPreviewRotation = null
        sensorHeadingTotal = 0f
        scaleObservations.clear()
        estimateHistory.clear()
        clockAgreementStreak = 0
    }

    /** Snapshots for future incremental rendering / backtracking. */
    fun recentEstimates(): List<Estimate> = estimateHistory.toList()

    fun process(
        frame: PanoramaSampleTimeline.FrameSample,
        matches: PanoramaSampleTimeline.SensorMatches
    ): Estimate = calculate(frame, matches).also { result ->
        estimateHistory.addLast(result)
        while (estimateHistory.size > 32 ||
            (estimateHistory.peekFirst()?.let {
                result.timestampNs - it.timestampNs > 4_000_000_000L
            } == true)) {
            estimateHistory.removeFirst()
        }
    }

    private fun calculate(
        frame: PanoramaSampleTimeline.FrameSample,
        matches: PanoramaSampleTimeline.SensorMatches
    ): Estimate {
        val preceding = previousFrameTimestampNs
        val precedingArrival = previousReceiveTimestampNs
        val rotationChanged = previousPreviewRotation != null &&
            previousPreviewRotation != frame.rotationDegrees
        previousPreviewRotation = frame.rotationDegrees
        if (rotationChanged) {
            // Coordinates change when CameraX rotates analysis frames. Never reuse
            // a pixel/radian calibration from the previous orientation.
            scaleObservations.clear()
            previousFrameHeading = null
            relativePose.reset()
            clockAgreementStreak = 0
        }
        previousFrameTimestampNs = frame.timestampNs
        previousReceiveTimestampNs = frame.receiveTimeNs
        val frameInterval = preceding?.let { (frame.timestampNs - it) * 1e-9f }
        val arrivalInterval = precedingArrival?.let { (frame.receiveTimeNs - it) * 1e-9f }

        // Reject camera sensor timestamp domains different from CLOCK_BOOTTIME.
        // Receive skew varies with camera/processing latency; tolerances are intentionally
        // generous, but adjacent intervals and sensor proximity must ALSO agree.
        val ageNs = frame.receiveTimeNs - frame.timestampNs
        val plausibleAge = ageNs in -40_000_000L..2_000_000_000L
        val plausibleCadence = frameInterval != null && arrivalInterval != null &&
            frameInterval in 0.025f..1.5f && arrivalInterval in 0.0f..2.0f &&
            abs(frameInterval - arrivalInterval) <= 0.45f
        val closeRotation = matches.rotation != null && matches.rotationGapNs != null &&
            matches.rotationGapNs <= MAX_IMU_GAP_NS
        val convertible = matches.rotation?.values?.let { rotationVectorMatrix(it) }
        val coherent = plausibleAge && closeRotation && convertible != null
        if (coherent) clockAgreementStreak++ else clockAgreementStreak = 0
        val synchronized = coherent && plausibleCadence && clockAgreementStreak >= 2

        // Establish a relative rotation-vector pose even on the initial frame; if a
        // sample is invalid, re-anchor the next usable segment instead of bridging it.
        val pose = if (coherent && convertible != null) relativePose.update(convertible) else null
        val currentHeading = if (pose?.reliable == true) pose.heading else null
        val previousHeading = previousFrameHeading
        val yawDelta = if (synchronized && previousHeading != null &&
            currentHeading != null && frameInterval != null) {
            val angle = wrappedDifference(currentHeading, previousHeading)
            angle.takeIf { abs(it) <= max(0.08f, 4.5f * frameInterval) }
        } else null
        previousFrameHeading = currentHeading
        if (!coherent) relativePose.reset()
        if (yawDelta != null) sensorHeadingTotal += yawDelta

        val visual = !rotationChanged && preceding != null && frameInterval != null &&
            frameInterval < 0.8f &&
            frame.opticalReliable && frame.trackedFeatures >= 4 &&
            frame.opticalInlierFraction.isFinite() && frame.opticalInlierFraction >= 0.65f &&
            frame.dx.isFinite() && frame.dy.isFinite() && abs(frame.dx) <= 14f &&
            abs(frame.dy) <= 11f
        val visualConfidence = if (visual) (
            0.48f + 0.29f * frame.opticalInlierFraction.coerceIn(0f, 1f) +
                0.17f * (frame.trackedFeatures / 10f).coerceIn(0f, 1f)
            ).coerceAtMost(0.91f) else 0f

        val gyro = matches.gyro?.values
        val gyroSpeed = if (synchronized && gyro != null && gyro.size >= 3 &&
            gyro.take(3).all { it.isFinite() })
            sqrt(gyro[0] * gyro[0] + gyro[1] * gyro[1] + gyro[2] * gyro[2]) else null
        val acceleration = matches.accelerometer?.values
        val accelerationNorm = if (synchronized && acceleration != null &&
            acceleration.size >= 3 && acceleration.take(3).all { it.isFinite() })
            sqrt(acceleration[0] * acceleration[0] +
                acceleration[1] * acceleration[1] + acceleration[2] * acceleration[2]) else null
        // Raw accelerometer is a shake / dynamic motion warning, not a position sensor.
        val movingHard = accelerationNorm != null && abs(accelerationNorm - 9.81f) > 4.5f
        // High angular velocity can cause feature matching to alias.
        val rapidMotion = gyroSpeed != null && gyroSpeed > 3.5f
        val imuStable = !rapidMotion && !movingHard

        var scale = calibratedScale()
        if (visual && yawDelta != null && imuStable &&
            abs(yawDelta) in 0.012f..0.26f && abs(frame.dx) >= 1f) {
            val ratio = frame.dx / yawDelta
            // A learned sign matters: flipping sign after a reversal is not calibration.
            if (ratio.isFinite() && abs(ratio) in 3f..1200f &&
                (scale == null || (ratio * scale > 0f &&
                    abs(ratio / scale) in 0.45f..2.0f))) {
                scaleObservations.addLast(ratio)
                while (scaleObservations.size > 11) scaleObservations.removeFirst()
                scale = calibratedScale()
            }
        }

        if (preceding == null) return Estimate(frame.timestampNs, Source.INITIAL, 0f, 0f, 0f,
            false, null, null, scale, null, "initial preview frame")

        if (visual && yawDelta != null && scale != null && imuStable) {
            val predicted = yawDelta * scale
            val disagreement = abs(frame.dx - predicted)
            val tolerance = max(2.5f, abs(predicted) * 0.65f)
            if (disagreement > tolerance) return Estimate(frame.timestampNs, Source.CONFLICT,
                frame.dx, frame.dy, min(0.40f, visualConfidence * 0.47f), synchronized,
                yawDelta, sensorHeadingTotal, scale, gyroSpeed,
                "optical/rotation disagreement: ${disagreement.toInt()}px")
            return Estimate(frame.timestampNs, Source.FUSED,
                0.85f * frame.dx + 0.15f * predicted, frame.dy,
                (visualConfidence * 0.98f).coerceAtMost(0.93f), true, yawDelta,
                sensorHeadingTotal, scale, gyroSpeed, "calibrated optical + IMU")
        }
        if (visual) return Estimate(frame.timestampNs, Source.VISUAL_ONLY,
            frame.dx, frame.dy, if (!imuStable) visualConfidence * 0.55f else visualConfidence,
            synchronized, yawDelta, if (yawDelta != null) sensorHeadingTotal else null,
            scale, gyroSpeed,
            if (rapidMotion) "fast sweep; optical without IMU correction"
            else if (movingHard) "high linear acceleration; optical only"
            else if (!synchronized) "camera/IMU clock not verified"
            else "not enough reliable angular/pixel calibration")
        if (yawDelta != null && scale != null && imuStable) {
            // Gyro bridge is an estimate, never a confirmed visual match. Vertical
            // displacement cannot be deduced from horizontal heading alone.
            return Estimate(frame.timestampNs, Source.IMU_ESTIMATED,
                (yawDelta * scale).coerceIn(-48f, 48f), 0f, 0.28f,
                true, yawDelta, sensorHeadingTotal, scale, gyroSpeed,
                "featureless / blurry; IMU bridge, vertical motion unknown")
        }
        return Estimate(frame.timestampNs, Source.UNTRACKED, 0f, 0f, 0f,
            synchronized, yawDelta, if (yawDelta != null) sensorHeadingTotal else null,
            scale, gyroSpeed,
            if (!synchronized) "no valid synchronized rotation-vector pair"
            else if (rapidMotion) "fast rotation without reliable visual match"
            else if (movingHard) "high linear acceleration; cannot trust IMU bridge"
            else if (rotationChanged) "preview rotation changed; recalibrating"
            else "insufficient visual features and uncalibrated motion")
    }

    private fun calibratedScale(): Float? {
        if (scaleObservations.size < 3) return null
        val values = scaleObservations.sorted()
        val median = values[values.size / 2]
        val close = values.count { it * median > 0f && abs(it - median) <= abs(median) * 0.45f }
        return if (close >= max(3, (values.size + 1) / 2)) median else null
    }

    private fun wrappedDifference(current: Float, previous: Float): Float {
        val tau = (Math.PI * 2).toFloat()
        var delta = current - previous
        while (delta > Math.PI.toFloat()) delta -= tau
        while (delta < -Math.PI.toFloat()) delta += tau
        return delta
    }

    companion object {
        private const val MAX_IMU_GAP_NS = 55_000_000L

        /** Android TYPE_ROTATION_VECTOR (x,y,z[,w]) to device-to-world row-major 3x3. */
        internal fun rotationVectorMatrix(values: FloatArray): FloatArray? {
            if (values.size < 3 || values.take(4).any { !it.isFinite() }) return null
            var x = values[0]
            var y = values[1]
            var z = values[2]
            var w = if (values.size >= 4) values[3]
                else sqrt(max(0f, 1f - x * x - y * y - z * z))
            val len = sqrt(x * x + y * y + z * z + w * w)
            if (!len.isFinite() || len < 0.6f || len > 1.4f) return null
            x /= len; y /= len; z /= len; w /= len
            return floatArrayOf(
                1f - 2f * (y * y + z * z), 2f * (x * y - z * w), 2f * (x * z + y * w),
                2f * (x * y + z * w), 1f - 2f * (x * x + z * z), 2f * (y * z - x * w),
                2f * (x * z - y * w), 2f * (y * z + x * w), 1f - 2f * (x * x + y * y)
            )
        }
    }
}
