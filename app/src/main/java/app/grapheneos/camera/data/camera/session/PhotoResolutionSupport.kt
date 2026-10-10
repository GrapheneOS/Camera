package app.grapheneos.camera.data.camera.session

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import app.grapheneos.camera.data.camera.model.PhotoResolutions
import javax.inject.Inject
import kotlin.math.roundToInt

interface PhotoResolutionSupport {

    /** Null when selecting the highest resolution would not give the camera more pixels. */
    fun resolutions(cameraInfo: CameraInfo): PhotoResolutions?
}

internal class PhotoResolutionSupportImpl @Inject constructor() : PhotoResolutionSupport {

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    override fun resolutions(cameraInfo: CameraInfo): PhotoResolutions? {
        val streams = try {
            Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP,
            )
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Camera info carries no camera2 characteristics", exception)
            null
        }
        val standard = streams?.getOutputSizes(ImageFormat.JPEG).largestMegapixels()
        val full = streams?.getHighResolutionOutputSizes(ImageFormat.JPEG).largestMegapixels()

        return when {
            standard == null || full == null || full <= standard -> null
            else -> PhotoResolutions(
                standardMegapixels = standard,
                fullMegapixels = full,
            )
        }
    }

    private fun Array<Size>?.largestMegapixels(): Int? {
        val pixels = this?.maxOfOrNull { it.width.toLong() * it.height } ?: return null

        return (pixels / PIXELS_PER_MEGAPIXEL).roundToInt()
    }

    private companion object {
        const val TAG = "PhotoResolutionSupport"
        const val PIXELS_PER_MEGAPIXEL = 1_000_000.0
    }
}
