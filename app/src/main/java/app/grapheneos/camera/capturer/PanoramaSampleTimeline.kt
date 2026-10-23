package app.grapheneos.camera.capturer

import java.util.ArrayDeque
import kotlin.math.abs

/**
 * Bounded, timestamp-addressable preview and sensor history.
 *
 * Pure Kotlin by design. All supplied pixel/sensor arrays are copied, so no CameraX
 * ImageProxy or Android SensorEvent can outlive its producer. No raw frames are saved
 * to disk. This is NOT a visual-inertial pose estimator or a live stitcher yet.
 *
 * Image timestamps are stored in the CameraX timestamp domain. SensorEvent timestamps
 * ordinarily use elapsed-realtime nanoseconds, but CAMERA timestamp-source support must
 * be checked on actual hardware before treating the two as a synchronized clock.
 */
internal class PanoramaSampleTimeline(
    private val maxFrames: Int = 32,
    private val maxSensors: Int = 768,
    private val windowNs: Long = 4_000_000_000L
) {
    init {
        require(maxFrames > 0 && maxSensors > 0 && windowNs > 0L)
    }

    enum class SensorKind { ROTATION_VECTOR, GYROSCOPE, ACCELEROMETER }

    data class FrameSample(
        val timestampNs: Long,
        val receiveTimeNs: Long,
        val width: Int,
        val height: Int,
        val rotationDegrees: Int,
        val luma: ByteArray,
        val opticalReliable: Boolean,
        val opticalInlierFraction: Float,
        val opticalSharpness: Float,
        val trackedFeatures: Int,
        val dx: Float,
        val dy: Float
    )

    data class SensorSample(
        val timestampNs: Long,
        val kind: SensorKind,
        val values: FloatArray
    )

    /** Sensor matches may improve after a *later* sensor event has arrived. */
    data class SensorMatches(
        val rotation: SensorSample?,
        val gyro: SensorSample?,
        val accelerometer: SensorSample?,
        val rotationGapNs: Long?
    )

    data class Neighbors(
        val previous: FrameSample?,
        val current: FrameSample?,
        val next: FrameSample?
    )

    data class Stats(
        val framesBuffered: Int,
        val sensorSamplesBuffered: Int,
        val frameBytes: Long,
        val framesAccepted: Long,
        val framesEvicted: Long,
        val frameTimestampsRejected: Long,
        val sensorSamplesAccepted: Long,
        val sensorsEvicted: Long,
        val sensorTimestampsRejected: Long,
        val lastFrameTimestampNs: Long?
    )

    private val frames = ArrayDeque<FrameSample>()
    private val sensors = ArrayDeque<SensorSample>()
    private val lastSensorTimestamp = HashMap<SensorKind, Long>()
    private var lastFrameTimestamp = Long.MIN_VALUE
    private var framesAccepted = 0L
    private var framesEvicted = 0L
    private var frameTimestampsRejected = 0L
    private var sensorSamplesAccepted = 0L
    private var sensorsEvicted = 0L
    private var sensorTimestampsRejected = 0L
    private var frameBytes = 0L

    @Synchronized
    fun addFrame(
        timestampNs: Long,
        receiveTimeNs: Long,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        luma: ByteArray,
        opticalReliable: Boolean,
        opticalInlierFraction: Float,
        opticalSharpness: Float,
        trackedFeatures: Int,
        dx: Float,
        dy: Float
    ): Boolean {
        require(width > 0 && height > 0 && luma.isNotEmpty())
        if (timestampNs <= 0L || timestampNs <= lastFrameTimestamp) {
            frameTimestampsRejected++
            return false
        }
        val frame = FrameSample(
            timestampNs, receiveTimeNs, width, height, rotationDegrees,
            luma.copyOf(), opticalReliable, opticalInlierFraction,
            opticalSharpness, trackedFeatures, dx, dy
        )
        lastFrameTimestamp = timestampNs
        frames.addLast(frame)
        frameBytes += frame.luma.size
        framesAccepted++
        while (frames.size > maxFrames ||
            (frames.peekFirst()?.let { timestampNs - it.timestampNs > windowNs } == true)) {
            frameBytes -= frames.removeFirst().luma.size
            framesEvicted++
        }
        return true
    }

    @Synchronized
    fun addSensor(timestampNs: Long, kind: SensorKind, values: FloatArray): Boolean {
        require(values.isNotEmpty())
        val last = lastSensorTimestamp[kind]
        if (timestampNs <= 0L || (last != null && timestampNs <= last)) {
            sensorTimestampsRejected++
            return false
        }
        lastSensorTimestamp[kind] = timestampNs
        sensors.addLast(SensorSample(timestampNs, kind, values.copyOf()))
        sensorSamplesAccepted++
        // In practice different sensors can arrive a little out of timestamp order.
        // Capacity always bounds the buffer; time expiry also walks the full deque.
        val iter = sensors.iterator()
        while (iter.hasNext()) {
            if (timestampNs - iter.next().timestampNs > windowNs) {
                iter.remove()
                sensorsEvicted++
            }
        }
        while (sensors.size > maxSensors) {
            sensors.removeFirst()
            sensorsEvicted++
        }
        return true
    }

    /** Returns only sensor readings close enough to make clock synchronization credible. */
    @Synchronized
    fun matchSensors(frameTimestampNs: Long, maxGapNs: Long = 100_000_000L): SensorMatches {
        require(maxGapNs >= 0L)
        var rotation: SensorSample? = null
        var gyro: SensorSample? = null
        var accel: SensorSample? = null
        var rotationGap = Long.MAX_VALUE
        var gyroGap = Long.MAX_VALUE
        var accelGap = Long.MAX_VALUE
        for (sample in sensors) {
            val gap = abs(sample.timestampNs - frameTimestampNs)
            if (gap > maxGapNs) continue
            when (sample.kind) {
                SensorKind.ROTATION_VECTOR -> if (gap < rotationGap) {
                    rotationGap = gap; rotation = sample
                }
                SensorKind.GYROSCOPE -> if (gap < gyroGap) {
                    gyroGap = gap; gyro = sample
                }
                SensorKind.ACCELEROMETER -> if (gap < accelGap) {
                    accelGap = gap; accel = sample
                }
            }
        }
        return SensorMatches(rotation?.copy(values = rotation.values.copyOf()),
            gyro?.copy(values = gyro.values.copyOf()),
            accel?.copy(values = accel.values.copyOf()),
            if (rotation == null) null else rotationGap)
    }

    /** Previous and subsequent cached frames, for later look-back / look-ahead matching. */
    @Synchronized
    fun neighbors(frameTimestampNs: Long): Neighbors {
        val ordered = frames.toList()
        val index = ordered.indexOfFirst { it.timestampNs == frameTimestampNs }
        if (index < 0) return Neighbors(null, null, null)
        return Neighbors(
            ordered.getOrNull(index - 1)?.copy(luma = ordered[index - 1].luma.copyOf()),
            ordered[index].copy(luma = ordered[index].luma.copyOf()),
            ordered.getOrNull(index + 1)?.copy(luma = ordered[index + 1].luma.copyOf())
        )
    }

    /** Snapshot for the motion estimator, independent of the internal image buffer. */
    @Synchronized
    fun newestFrame(): FrameSample? = frames.peekLast()?.let {
        it.copy(luma = it.luma.copyOf())
    }

    @Synchronized
    fun stats(): Stats = Stats(
        frames.size, sensors.size, frameBytes, framesAccepted, framesEvicted,
        frameTimestampsRejected, sensorSamplesAccepted, sensorsEvicted,
        sensorTimestampsRejected, frames.peekLast()?.timestampNs
    )

    @Synchronized
    fun clear() {
        frames.clear()
        sensors.clear()
        lastSensorTimestamp.clear()
        lastFrameTimestamp = Long.MIN_VALUE
        frameBytes = 0L
        framesAccepted = 0L
        framesEvicted = 0L
        frameTimestampsRejected = 0L
        sensorSamplesAccepted = 0L
        sensorsEvicted = 0L
        sensorTimestampsRejected = 0L
    }
}
