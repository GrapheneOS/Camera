package app.grapheneos.camera.analyzer

import android.util.Log
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageProxy
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap
import kotlin.math.roundToInt

class QRAnalyzer(private val mActivity: MainActivity) : Analyzer {
    companion object {
        private const val TAG = "QRCodeImageAnalyzer"
    }

    private var frameCounter = 0
    private var lastFpsTimestamp = System.nanoTime()

    private val reader = MultiFormatReader()
    private var imageData = ByteArray(0)

    init {
        refreshHints()
    }

    fun refreshHints() {
        val supportedHints: MutableMap<DecodeHintType, Any> = EnumMap(
            DecodeHintType::class.java
        )

        Log.i(TAG, "allowedFormats: ${mActivity.config.allowedFormats}")

        supportedHints[DecodeHintType.POSSIBLE_FORMATS] =
            mActivity.config.allowedFormats

        reader.setHints(supportedHints)
    }

    override fun analyze(image: ImageProxy) {
        val plane = image.planes[0]
        val byteBuffer = plane.buffer
        val rotationDegrees = image.imageInfo.rotationDegrees

        if (imageData.size != byteBuffer.capacity()) {
            imageData = ByteArray(byteBuffer.capacity())
        }
        byteBuffer.get(imageData)

        val previewWidth: Int
        val previewHeight: Int

        val imageWidth : Int
        val imageHeight : Int

        imageData = fixOrientation(imageData, image.width, image.height, rotationDegrees)

        if (rotationDegrees == 0 || rotationDegrees == 180) {
            previewWidth = mActivity.previewView.height
            previewHeight = mActivity.previewView.width

            imageWidth = plane.rowStride
            imageHeight = image.height
        } else {
            previewWidth = mActivity.previewView.width
            previewHeight = mActivity.previewView.height

            imageWidth = image.height
            imageHeight = image.width
        }

        val iFact = if (previewWidth < previewHeight) {
            imageWidth / previewWidth.toFloat()
        } else {
            imageHeight / previewHeight.toFloat()
        }

        val size = mActivity.qrOverlay.size * iFact

        val left = (imageWidth - size) / 2
        val top = (imageHeight - size) / 2

        val source = PlanarYUVLuminanceSource(
            imageData,
            imageWidth, imageHeight,
            left.roundToInt(), top.roundToInt(),
            size.roundToInt(), size.roundToInt(),
            false
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        reader.reset()
        try {
            reader.decodeWithState(binaryBitmap).text?.let {
                mActivity.onScanResultSuccess(it)
            }
        } catch (e: ReaderException) {
            val invertedSource = source.invert()
            val invertedBinaryBitmap = BinaryBitmap(HybridBinarizer(invertedSource))
            reader.reset()
            try {
                reader.decodeWithState(invertedBinaryBitmap).text?.let {
                    mActivity.onScanResultSuccess(it)
                }
            } catch (e: ReaderException) {
            }
        }

        // Compute the FPS of the entire pipeline
        val frameCount = 10
        if (++frameCounter % frameCount == 0) {
            frameCounter = 0
            val now = System.nanoTime()
            val delta = now - lastFpsTimestamp
            val fps = 1_000_000_000 * frameCount.toFloat() / delta
            Log.d(TAG, "Analysis FPS: ${"%.02f".format(fps)}")
            lastFpsTimestamp = now
        }

        image.close()
    }

    private fun fixOrientation(
        data: ByteArray,
        imageWidth: Int,
        imageHeight: Int,
        rotation: Int
    ): ByteArray {
        return when(rotation) {
            90 -> rotate90(data, imageWidth, imageHeight)
            180 -> rotate180(data, imageWidth, imageHeight)
            270 -> rotate270(data, imageWidth, imageHeight)
            else -> imageData
        }
    }

    private fun rotate90(
        data: ByteArray,
        imageWidth: Int,
        imageHeight: Int
    ) : ByteArray {

        val yuv = ByteArray(imageWidth * imageHeight)
        var i = 0
        for (x in 0 until imageWidth) {
            for (y in imageHeight - 1 downTo 0) {
                yuv[i++] = data[y * imageWidth + x]
            }
        }

        return yuv
    }

    private fun rotate180(
        data: ByteArray,
        imageWidth: Int,
        imageHeight: Int
    ): ByteArray {
        val yuv = ByteArray(imageWidth * imageHeight)
        var count = 0
        var i: Int = imageWidth * imageHeight - 1
        while (i >= 0) {
            yuv[count] = data[i]
            count++
            i--
        }

        return yuv
    }

    private fun rotate270(
        data: ByteArray, imageWidth: Int,
        imageHeight: Int
    ): ByteArray {
        return rotate180(
            rotate90(data, imageWidth, imageHeight),
            imageWidth, imageHeight
        )
    }
}
