package app.grapheneos.camera.util

import android.graphics.ImageDecoder
import kotlin.math.max

class ImageResizer(val targetWidth: Int, val targetHeight: Int) : ImageDecoder.OnHeaderDecodedListener {
    override fun onHeaderDecoded(decoder: ImageDecoder, info: ImageDecoder.ImageInfo, source: ImageDecoder.Source) {
        val size = info.size
        val w = size.width.toDouble()
        val h = size.height.toDouble()

        val ratio = max(w / targetWidth, h / targetHeight)
        decoder.setTargetSize((w / ratio).toInt(), (h / ratio).toInt())
    }
}
