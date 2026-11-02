package app.grapheneos.camera.analyzer

import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageProxy
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap
import kotlin.math.roundToInt

class QRAnalyzer(private val mActivity: MainActivity) : Analyzer {
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

        image.close()
    }
}
