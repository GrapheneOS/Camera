package app.grapheneos.camera.capturer

import android.graphics.Bitmap
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
/** Bounded-memory lighten stack. Stationary tripod is essential for aligned trails. */
internal class LightTrailsModule : Closeable {
    private var base: IntArray? = null
    private var trail: IntArray? = null
    private var width = 0
    private var height = 0
    var frames: Int = 0
        private set

    fun addPhoto(file: File) {
        val bitmap = decodePhoto(file, MAX_PIXELS) ?: error("Cannot decode photo for stack")
        try {
            if (base == null) {
                width = bitmap.width
                height = bitmap.height
            }
            val scaled = if (bitmap.width != width || bitmap.height != height) {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else bitmap
            try {
                val pixels = IntArray(width * height)
                scaled.getPixels(pixels, 0, width, 0, 0, width, height)
                val reference = base
                val result = trail
                if (reference == null || result == null) {
                    base = pixels
                    trail = pixels.clone()
                } else {
                    for (i in pixels.indices) {
                        val current = pixels[i]
                        val original = reference[i]
                        val old = result[i]
                        val newLum = luminance(current)
                        val oldLum = luminance(old)
                        // Require a meaningful transient highlight above the first (base) frame.
                        // Suppress small ambient changes and sensor-noise accumulation.
                        if (newLum > luminance(original) + HIGHLIGHT_THRESHOLD && newLum > oldLum) {
                            result[i] = current
                        }
                    }
                }
                frames++
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun luminance(p: Int): Int =
        ((p shr 16 and 255) * 54 + (p shr 8 and 255) * 183 + (p and 255) * 19) shr 8

    fun saveJpeg(destination: File) {
        val pixels = trail ?: error("No stack frames")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            val temp = File(destination.parentFile, "${destination.name}.writing")
            try {
                FileOutputStream(temp).use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output))
                    output.fd.sync()
                }
                check(temp.renameTo(destination)) { "Could not commit composite" }
            } finally {
                temp.delete()
            }
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        base = null
        trail = null
    }

    companion object {
        private const val MAX_PIXELS = 2_300_000
        private const val HIGHLIGHT_THRESHOLD = 16
    }
}
