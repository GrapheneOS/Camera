package app.grapheneos.camera.data.camera.session

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.grapheneos.camera.data.camera.model.QR_SCAN_AREA_RATIO
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

internal class QrCodeAnalyzer(
    barcodeFormats: Set<BarcodeFormat>,
    private val onCodeScanned: (text: String) -> Unit,
) : ImageAnalysis.Analyzer {

    private var frameCounter = 0
    private var lastFpsTimestamp = System.nanoTime()

    private val reader = MultiFormatReader()
    private var imageData = ByteArray(0)

    init {
        setBarcodeFormats(barcodeFormats)
    }

    fun setBarcodeFormats(barcodeFormats: Set<BarcodeFormat>) {
        Log.i(TAG, "barcodeFormats: $barcodeFormats")

        val hints: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)
        hints[DecodeHintType.POSSIBLE_FORMATS] = barcodeFormats

        reader.setHints(hints)
    }

    override fun analyze(image: ImageProxy) {
        val plane = image.planes[0]
        val byteBuffer = plane.buffer

        if (imageData.size != byteBuffer.capacity()) {
            imageData = ByteArray(byteBuffer.capacity())
        }
        byteBuffer.get(imageData)

        // The overlay covers the preview and its square is a share of the preview's shorter side.
        // Frames are rotated with the device rather than the display, but however they are turned
        // their shorter side shows what the preview's shorter side does.
        val size = min(image.width, image.height) * QR_SCAN_AREA_RATIO

        val left = (image.width - size) / 2
        val top = (image.height - size) / 2

        val source = PlanarYUVLuminanceSource(
            imageData,
            plane.rowStride,
            image.height,
            left.roundToInt(),
            top.roundToInt(),
            size.roundToInt(),
            size.roundToInt(),
            false,
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        reader.reset()
        try {
            reader.decodeWithState(binaryBitmap).text?.let(onCodeScanned)
        } catch (_: ReaderException) {
            val invertedSource = source.invert()
            val invertedBinaryBitmap = BinaryBitmap(HybridBinarizer(invertedSource))
            reader.reset()
            try {
                reader.decodeWithState(invertedBinaryBitmap).text?.let(onCodeScanned)
            } catch (_: ReaderException) {
            }
        }

        logFps()

        image.close()
    }

    private fun logFps() {
        if (++frameCounter % FPS_FRAME_COUNT != 0) return

        frameCounter = 0
        val now = System.nanoTime()
        val delta = now - lastFpsTimestamp
        val fps = NANOS_PER_SECOND * FPS_FRAME_COUNT.toFloat() / delta
        Log.d(TAG, "Analysis FPS: ${"%.02f".format(Locale.ROOT, fps)}")
        lastFpsTimestamp = now
    }

    private companion object {
        const val TAG = "QRCodeImageAnalyzer"
        const val FPS_FRAME_COUNT = 10
        const val NANOS_PER_SECOND = 1_000_000_000
    }
}
