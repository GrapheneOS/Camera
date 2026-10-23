package app.grapheneos.camera.portrait

import kotlin.math.roundToInt

/** Interpolates portrait blur-layer colors with bounded input values. */
internal object PortraitCompositeMath {
    fun mixGaussianRgb(near: Int, mid: Int, far: Int, strength: Float): Int {
        val safe = if (strength.isFinite()) strength.coerceIn(0f, 1f) else .48f
        val lower: Int
        val upper: Int
        val t: Float
        if (safe < .5f) {
            lower = near
            upper = mid
            t = (safe / .5f).coerceIn(0f, 1f)
        } else {
            lower = mid
            upper = far
            t = ((safe - .5f) / .5f).coerceIn(0f, 1f)
        }
        fun channel(shift: Int): Int {
            val first = (lower ushr shift) and 255
            val second = (upper ushr shift) and 255
            return (first + (second - first) * t).roundToInt().coerceIn(0, 255)
        }
        return (0xff shl 24) or (channel(16) shl 16) or
            (channel(8) shl 8) or channel(0)
    }
}
