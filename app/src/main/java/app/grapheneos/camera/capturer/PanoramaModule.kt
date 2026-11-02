package app.grapheneos.camera.capturer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.tan

/** JPEG decoding and cylindrical projection shared by the panorama engines. */
internal object PanoramaModule {
    data class Frame(val source: File, val rotation: Int, val heading: Float,
                     val focalPixels: Float = Float.NaN)
    data class Result(val bitmap: Bitmap, val gyroFallbackPairs: Int)

    internal fun orientedSize(frame: Frame): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(frame.source.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) {
            "Cannot read original panorama JPEG ${frame.source.name}"
        }
        val angle = ((frame.rotation % 360) + 360) % 360
        require(angle == 0 || angle == 90 || angle == 180 || angle == 270) {
            "Unsupported JPEG orientation $angle"
        }
        return if (angle == 90 || angle == 270)
            options.outHeight to options.outWidth
        else options.outWidth to options.outHeight
    }

    /** Decode every source only when needed; no 25-full-bitmap heap spike. */
    internal fun decodeFrame(frame: Frame, width: Int, height: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(frame.source.absolutePath, bounds)
        val angle = ((frame.rotation % 360) + 360) % 360
        val sourceWidth = if (angle == 90 || angle == 270) bounds.outHeight else bounds.outWidth
        val sourceHeight = if (angle == 90 || angle == 270) bounds.outWidth else bounds.outHeight
        require(sourceWidth > 0 && sourceHeight > 0) { "Invalid JPEG ${frame.source.name}" }
        var sample = 1
        while (sourceWidth / (sample * 2) >= width && sourceHeight / (sample * 2) >= height) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(frame.source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false })
            ?: throw IllegalArgumentException("Cannot decode ${frame.source.name}")
        val oriented = if (angle == 0) decoded else {
            try {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                    Matrix().apply { postRotate(angle.toFloat()) }, true)
            } finally { decoded.recycle() }
        }
        if (oriented.width == width && oriented.height == height) return oriented
        return try { Bitmap.createScaledBitmap(oriented, width, height, true) }
            finally { oriented.recycle() }
    }

    internal class Cylinder(val inputWidth: Int, val inputHeight: Int, val focal: Double) {
        private val maximumAngle = atan(inputWidth / (2 * focal))
        val width = (2 * focal * maximumAngle).roundToInt()
        val height = (inputHeight * cos(maximumAngle) * 0.995).roundToInt()

        fun warp(input: Bitmap): Bitmap {
            require(input.width == inputWidth && input.height == inputHeight)
            val source = IntArray(inputWidth * inputHeight)
            input.getPixels(source, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            // Render in short row batches to limit memory use.
            val projected = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val tileRows = 24
            val rowPixels = IntArray(width * tileRows)
            val centerX = (inputWidth - 1) / 2.0
            val centerY = (inputHeight - 1) / 2.0
            val outputCenterX = (width - 1) / 2.0
            val outputCenterY = (height - 1) / 2.0
            val sourceX = DoubleArray(width)
            val verticalScale = DoubleArray(width)
            for (x in 0 until width) {
                val angle = (x - outputCenterX) / focal
                sourceX[x] = centerX + focal * tan(angle)
                verticalScale[x] = 1.0 / cos(angle)
            }
            for (y in 0 until height) {
                val outputRow = (y % tileRows) * width
                for (x in 0 until width) {
                    val sx = sourceX[x].coerceIn(0.0, inputWidth - 1.001)
                    val sy = (centerY + (y - outputCenterY) * verticalScale[x])
                        .coerceIn(0.0, inputHeight - 1.001)
                    val ix = sx.toInt()
                    val iy = sy.toInt()
                    val fx = sx - ix
                    val fy = sy - iy
                    val a = source[iy * inputWidth + ix]
                    val b = source[iy * inputWidth + ix + 1]
                    val c = source[(iy + 1) * inputWidth + ix]
                    val d = source[(iy + 1) * inputWidth + ix + 1]
                    rowPixels[outputRow + x] = bilinear(a, b, c, d, fx, fy)
                }
                if ((y + 1) % tileRows == 0 || y == height - 1) {
                    val rows = (y % tileRows) + 1
                    projected.setPixels(rowPixels, 0, width, 0, y + 1 - rows, width, rows)
                }
            }
            return projected
        }
    }

    private fun bilinear(a: Int, b: Int, c: Int, d: Int, fx: Double, fy: Double): Int {
        val top = 1 - fy
        val left = 1 - fx
        fun channel(shift: Int): Int {
            val value = ((a ushr shift and 255) * left + (b ushr shift and 255) * fx) * top +
                ((c ushr shift and 255) * left + (d ushr shift and 255) * fx) * fy
            return value.roundToInt().coerceIn(0, 255)
        }
        return -0x1000000 or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
