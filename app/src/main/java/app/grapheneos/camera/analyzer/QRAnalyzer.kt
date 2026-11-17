package app.grapheneos.camera.analyzer

import android.util.Log
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageProxy
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.HybridBinarizer
import java.nio.charset.StandardCharsets
import java.util.EnumMap
import kotlin.math.roundToInt

class QRAnalyzer(private val mActivity: MainActivity) : Analyzer {
    companion object {
        private const val TAG = "QRCodeImageAnalyzer"

        // an ECI overrides the charset, and only QR and Data Matrix report whether one was used
        private val NO_ECI_QR = setOf("]Q1", "]Q3", "]Q5")
        private val NO_ECI_DATA_MATRIX = setOf("]d1", "]d2", "]d3")
    }

    private var frameCounter = 0
    private var lastFpsTimestamp = System.nanoTime()

    private val reader = MultiFormatReader()
    // decodes QR byte segments as ISO-8859-1, which maps each byte to one char
    private val rawReader = MultiFormatReader()
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
        rawReader.setHints(EnumMap(supportedHints).apply {
            put(DecodeHintType.CHARACTER_SET, StandardCharsets.ISO_8859_1.name())
        })
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
            val result = reader.decodeWithState(binaryBitmap)
            result.text?.let {
                mActivity.onScanResultSuccess(it) { payloadBytes(result, binaryBitmap) }
            }
        } catch (e: ReaderException) {
            val invertedSource = source.invert()
            val invertedBinaryBitmap = BinaryBitmap(HybridBinarizer(invertedSource))
            reader.reset()
            try {
                val result = reader.decodeWithState(invertedBinaryBitmap)
                result.text?.let {
                    mActivity.onScanResultSuccess(it) { payloadBytes(result, invertedBinaryBitmap) }
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

    private fun payloadBytes(result: Result, bitmap: BinaryBitmap): ByteArray {
        val symbology =
            result.resultMetadata?.get(ResultMetadataType.SYMBOLOGY_IDENTIFIER) as? String
        if (symbology in NO_ECI_DATA_MATRIX) {
            // Data Matrix ignores the charset hint and always decodes binary data as ISO-8859-1
            return result.text.toByteArray(StandardCharsets.ISO_8859_1)
        }
        if (symbology in NO_ECI_QR) {
            try {
                rawReader.reset()
                val raw = rawReader.decodeWithState(bitmap)
                val segments = raw.resultMetadata?.get(ResultMetadataType.BYTE_SEGMENTS) as? List<*>
                val segmentHighBytes = segments.orEmpty().filterIsInstance<ByteArray>()
                    .sumOf { segment -> segment.count { it < 0 } }
                // Kanji and Hanzi ignore the hint, so only byte segments may contain non-ASCII
                if (raw.text.count { it.code >= 0x80 } == segmentHighBytes) {
                    return raw.text.toByteArray(StandardCharsets.ISO_8859_1)
                }
            } catch (e: ReaderException) {
            }
        }
        return result.text.toByteArray(StandardCharsets.UTF_8)
    }
}
