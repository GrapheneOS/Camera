package app.grapheneos.camera.capturer

import kotlin.math.abs

/** Pure-JVM admission policy; timestamps use monotonic uptime milliseconds. */
internal object PanoramaStreamPolicy {
    const val MIN_INTERVAL_MS = 180L
    const val MIN_YAW_DELTA_RADIANS = 0.050f
    const val MAX_SEGMENTS = 72

    fun accept(heading: Float, previousHeading: Float?, nowMs: Long,
               previousAtMs: Long, count: Int): Boolean =
        heading.isFinite() && count < MAX_SEGMENTS &&
        (previousHeading == null ||
            (nowMs >= previousAtMs + MIN_INTERVAL_MS &&
                abs(heading - previousHeading) >= MIN_YAW_DELTA_RADIANS))

    fun angleFraction(first: Float?, current: Float, target: Float): Float {
        if (first == null || !first.isFinite() || !current.isFinite() ||
            !target.isFinite() || target <= 0f) return 0f
        return (abs(current - first) / target).coerceIn(0f, 1f)
    }
}
