package app.grapheneos.camera.capturer

import java.nio.ByteBuffer

/**
 * Safely copies a *small* upright RGB sample from CameraX YUV_420_888 planes.
 * CameraX ImageProxy and plane buffers are NEVER retained after this call.
 * Handles independent row strides, pixel strides and all quarter-turn rotations.
 */
internal object PanoramaYuvColorSampler {
    const val WIDTH = 160
    const val HEIGHT = 128

    data class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)
    data class Frame(val width: Int, val height: Int, val argb: IntArray)

    fun sample(sourceWidth: Int, sourceHeight: Int, rotationDegrees: Int,
               y: Plane, u: Plane, v: Plane,
               outputWidth: Int = WIDTH, outputHeight: Int = HEIGHT): Frame {
        require(sourceWidth >= 2 && sourceHeight >= 2 &&
            outputWidth > 0 && outputHeight > 0 && rotationDegrees % 90 == 0)
        val angle = (rotationDegrees % 360 + 360) % 360
        val turned = angle == 90 || angle == 270
        val orientedWidth = if (turned) sourceHeight else sourceWidth
        val orientedHeight = if (turned) sourceWidth else sourceHeight
        val pixels = IntArray(outputWidth * outputHeight)
        for (outY in 0 until outputHeight) {
            val py = ((outY + 0.5f) * orientedHeight / outputHeight).toInt()
                .coerceIn(0, orientedHeight - 1)
            for (outX in 0 until outputWidth) {
                val px = ((outX + 0.5f) * orientedWidth / outputWidth).toInt()
                    .coerceIn(0, orientedWidth - 1)
                val sx: Int
                val sy: Int
                when (angle) {
                    90 -> { sx = py; sy = sourceHeight - 1 - px }
                    180 -> { sx = sourceWidth - 1 - px; sy = sourceHeight - 1 - py }
                    270 -> { sx = sourceWidth - 1 - py; sy = px }
                    else -> { sx = px; sy = py }
                }
                val luma = read(y, sx, sy)
                val chromaU = read(u, sx / 2, sy / 2) - 128
                val chromaV = read(v, sx / 2, sy / 2) - 128
                val c = (luma - 16).coerceAtLeast(0)
                val red = ((298 * c + 409 * chromaV + 128) shr 8).coerceIn(0, 255)
                val green = ((298 * c - 100 * chromaU - 208 * chromaV + 128) shr 8)
                    .coerceIn(0, 255)
                val blue = ((298 * c + 516 * chromaU + 128) shr 8).coerceIn(0, 255)
                pixels[outY * outputWidth + outX] =
                    -0x1000000 or (red shl 16) or (green shl 8) or blue
            }
        }
        return Frame(outputWidth, outputHeight, pixels)
    }

    private fun read(plane: Plane, x: Int, y: Int): Int {
        val at = y * plane.rowStride + x * plane.pixelStride
        require(plane.rowStride > 0 && plane.pixelStride > 0 && at in 0 until plane.buffer.limit())
        return plane.buffer.get(at).toInt() and 255
    }
}
