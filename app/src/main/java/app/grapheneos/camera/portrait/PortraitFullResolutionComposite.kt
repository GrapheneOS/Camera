package app.grapheneos.camera.portrait

import kotlin.math.roundToInt

/**
 * Final full-resolution RGB portrait composite (Android-independent).
 * All opaque subject pixels remain bit-identical to decoded capture pixels;
 * only the background or soft matte boundary is composited with Gaussian RGB.
 * This does not pretend a resized MODNet segmentation contains extra detail.
 */
internal object PortraitFullResolutionComposite {
    fun overlayAlpha(modelAlpha: Float, refinedAlpha: Float, strength: Float): Int {
        // Unreliable matte data must NOT blur the subject.
        if (!modelAlpha.isFinite() || !refinedAlpha.isFinite()) return 0
        val m = modelAlpha.coerceIn(0f, 1f)
        val r = refinedAlpha.coerceIn(0f, 1f)
        // A high-confidence foreground uses only original camera RGB.
        // Both estimates have to agree; feathered hair remains transparent.
        val subject = if (m >= .95f && r >= .95f) 1f
            else (m * .65f + r * .35f).coerceIn(0f, 1f)
        val s = if (strength.isFinite()) strength.coerceIn(0f, 1f) else 0f
        return ((1f - subject) * s * 255f).roundToInt().coerceIn(0, 255)
    }

    fun blendOpaque(foreground: Int, background: Int, overlay: Int): Int {
        val alpha = overlay.coerceIn(0, 255)
        if (alpha == 0) return foreground
        if (alpha == 255) return 0xff000000.toInt() or (background and 0x00ffffff)
        val inv = 255 - alpha
        fun mix(shift: Int): Int {
            val front = (foreground ushr shift) and 255
            val back = (background ushr shift) and 255
            return (front * inv + back * alpha + 127) / 255
        }
        return (0xff shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}
