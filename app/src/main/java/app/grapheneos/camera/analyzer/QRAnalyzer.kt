package app.grapheneos.camera.analyzer

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class QRAnalyzer(private val mActivity: MainActivity) : ImageAnalysis.Analyzer {

    private var multiFormatReader: MultiFormatReader = MultiFormatReader()
    private var isScanning = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {

        if (isScanning.get()) {
            image.close()
            return
        }

        if (mActivity.qrOverlay.size==0f)
            return

        isScanning.set(true)

        val rotatedImage = RotatedImage(getLuminancePlaneData(image),
            image.width, image.height)
        rotateImageArray(rotatedImage, image.imageInfo.rotationDegrees)


        val iFact = if (mActivity.qrOverlay.width < mActivity.qrOverlay.height) {
            rotatedImage.width /
                    mActivity.qrOverlay.width.toFloat()
        } else {
            rotatedImage.height /
                    mActivity.qrOverlay.height.toFloat()
        }

        val size = mActivity.qrOverlay.size * iFact

        val left = (rotatedImage.width - size) / 2
        val top = (rotatedImage.height - size) / 2

        val planarYUVLuminanceSource = PlanarYUVLuminanceSource(
            rotatedImage.byteArray,
            rotatedImage.width, rotatedImage.height,
            left.roundToInt(), top.roundToInt(),
            size.roundToInt(), size.roundToInt(),
            false
        )
        val hybridBinarizer = HybridBinarizer(planarYUVLuminanceSource)
        val binaryBitmap = BinaryBitmap(hybridBinarizer)
        try {
            val rawResult = multiFormatReader.decodeWithState(binaryBitmap)
            rawResult?.text?.let {
                mActivity.onScanResultSuccess(it)
            }
        } catch (e: NotFoundException) {
            e.printStackTrace()
        } finally {
            multiFormatReader.reset()
            image.close()
        }

        isScanning.set(false)
    }

    // 90, 180. 270 rotation
    private fun rotateImageArray(imageToRotate: RotatedImage, rotationDegrees: Int) {
        if (rotationDegrees == 0) return // no rotation
        if (rotationDegrees % 90 != 0) return // only 90 degree times rotations

        val width = imageToRotate.width
        val height = imageToRotate.height

        val rotatedData = ByteArray(imageToRotate.byteArray.size)
        for (y in 0 until height) { // we scan the array by rows
            for (x in 0 until width) {
                when (rotationDegrees) {
                    90 -> rotatedData[x * height + height - y - 1] =
                        imageToRotate.byteArray[x + y * width] // Fill from top-right toward left (CW)
                    180 -> rotatedData[width * (height - y - 1) + width - x - 1] =
                        imageToRotate.byteArray[x + y * width] // Fill from bottom-right toward up (CW)
                    270 -> rotatedData[y + x * height] =
                        imageToRotate.byteArray[y * width + width - x - 1] // The opposite (CCW) of 90 degrees
                }
            }
        }

        imageToRotate.byteArray = rotatedData

        if (rotationDegrees != 180) {
            imageToRotate.height = width
            imageToRotate.width = height
        }
    }

    /**
     * IMPORTANT: There's a known issue with the combination of CameraX and Zxing in some phones,
     * especially OnePlus phones, where zxing doesn't detect any QR/Barcodes at all.
     *
     * To resolve that issue, we're required to cleanup the image data with this fix method
     * @see <a href="https://github.com/beemdevelopment/Aegis/commit/fb58c877d1b305b1c66db497880da5651dda78d7">Aegis Authenticator Github Commit</a>
     *
     * @param image imageProxy from camera analyzer
     * @return cleaned image bytearray
     */
    private fun getLuminancePlaneData(image: ImageProxy): ByteArray {
        val plane = image.planes[0]
        val buf: ByteBuffer = plane.buffer
        val data = ByteArray(buf.remaining())
        buf.get(data)
        buf.rewind()
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        // remove padding from the Y plane data
        val cleanData = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                cleanData[y * width + x] = data[y * rowStride + x * pixelStride]
            }
        }
        return cleanData
    }


    private class RotatedImage(var byteArray: ByteArray, var width: Int, var height: Int)
}
