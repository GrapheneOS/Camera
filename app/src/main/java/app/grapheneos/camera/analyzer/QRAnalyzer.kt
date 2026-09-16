package app.grapheneos.camera.analyzer

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageProxy
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap
import kotlin.math.roundToInt

class QRAnalyzer(private val mActivity: MainActivity) : Analyzer {
    companion object {
        private const val TAG = "QRCodeImageAnalyzer"

        fun scanStillImage(bitmap: Bitmap, possibleFormats: List<BarcodeFormat>): String? {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val hints: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)
            hints[DecodeHintType.POSSIBLE_FORMATS] = possibleFormats
            hints[DecodeHintType.TRY_HARDER] = true

            val reader = MultiFormatReader().apply { setHints(hints) }

            val source = RGBLuminanceSource(width, height, pixels)
            decodeOrNull(reader, BinaryBitmap(HybridBinarizer(source)))?.let { return it }
            return decodeOrNull(reader, BinaryBitmap(HybridBinarizer(source.invert())))
        }

        private fun decodeOrNull(reader: MultiFormatReader, binaryBitmap: BinaryBitmap): String? {
            reader.reset()
            return try {
                reader.decodeWithState(binaryBitmap).text
            } catch (e: ReaderException) {
                null
            }
        }
    }

    private var frameCounter = 0
    private var lastFpsTimestamp = System.nanoTime()

    private val reader = MultiFormatReader()
    private var imageData = ByteArray(0)

    init {
        refreshHints()
    }

    fun refreshHints() {
        val camConfig = mActivity.camConfig

        val supportedHints: MutableMap<DecodeHintType, Any> = EnumMap(
            DecodeHintType::class.java
        )

        Log.i(TAG, "allowedFormats: ${camConfig.allowedFormats}")

        supportedHints[DecodeHintType.POSSIBLE_FORMATS] =
            if (camConfig.scanAllCodes) {
                BarcodeFormat.values().asList()
            } else {
                camConfig.allowedFormats
            }

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

        if (rotationDegrees == 0 || rotationDegrees == 180) {
            previewWidth = mActivity.previewView.width
            previewHeight = mActivity.previewView.height
        } else {
            previewWidth = mActivity.previewView.height
            previewHeight = mActivity.previewView.width
        }

        val scaleFactor = if (previewWidth < previewHeight) {
            image.width / previewWidth.toFloat()
        } else {
            image.height / previewHeight.toFloat()
        }

        val size = mActivity.qrOverlay.size * scaleFactor

        val left = (image.width - size) / 2
        val top = (image.height - size) / 2

        val source = PlanarYUVLuminanceSource(
            imageData,
            plane.rowStride, image.height,
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
}
