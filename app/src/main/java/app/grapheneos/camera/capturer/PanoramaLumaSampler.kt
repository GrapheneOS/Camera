package app.grapheneos.camera.capturer

import java.nio.ByteBuffer

/** Copies an upright, compact Y-plane preview without retaining CameraX buffers. */
internal object PanoramaLumaSampler {
    fun sample(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        rotationDegrees: Int,
        outputWidth: Int,
        outputHeight: Int
    ): ByteArray {
        require(width > 0 && height > 0 && rowStride > 0 && pixelStride > 0)
        require(outputWidth > 0 && outputHeight > 0)
        val rotation = ((rotationDegrees % 360) + 360) % 360
        require(rotation % 90 == 0)
        val rotated = rotation == 90 || rotation == 270
        val uprightWidth = if (rotated) height else width
        val uprightHeight = if (rotated) width else height
        val output = ByteArray(outputWidth * outputHeight)
        for (y in 0 until outputHeight) {
            val py = ((y + 0.5f) * uprightHeight / outputHeight).toInt()
                .coerceIn(0, uprightHeight - 1)
            for (x in 0 until outputWidth) {
                val px = ((x + 0.5f) * uprightWidth / outputWidth).toInt()
                    .coerceIn(0, uprightWidth - 1)
                val sx: Int
                val sy: Int
                when (rotation) {
                    90 -> { sx = py; sy = height - 1 - px }
                    180 -> { sx = width - 1 - px; sy = height - 1 - py }
                    270 -> { sx = width - 1 - py; sy = px }
                    else -> { sx = px; sy = py }
                }
                output[y * outputWidth + x] = buffer.get(sy * rowStride + sx * pixelStride)
            }
        }
        return output
    }
}
