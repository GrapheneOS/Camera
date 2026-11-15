package app.grapheneos.camera.data.camera.session

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraExtensionCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import app.grapheneos.camera.data.core.model.ExtensionMode
import javax.inject.Inject

interface LowLightBoostSupport {

    fun isSupported(
        cameraInfo: CameraInfo,
        extensionMode: ExtensionMode?,
    ): Boolean
}

// CameraX 1.6.2 answers for the camera without its extension. An extension session applies only
// the request keys its extension lists and overrides the rest, and an extension may advertise
// exposure modes of its own, which Camera2CameraInfo returns in place of the camera's.
internal class LowLightBoostSupportImpl @Inject constructor(
    private val cameraManager: CameraManager,
) : LowLightBoostSupport {

    override fun isSupported(
        cameraInfo: CameraInfo,
        extensionMode: ExtensionMode?,
    ): Boolean {
        return when {
            !cameraInfo.isLowLightBoostSupported -> false
            extensionMode == null -> true
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM -> false
            else -> isSupportedByExtension(
                cameraInfo = cameraInfo,
                extensionMode = extensionMode,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun isSupportedByExtension(
        cameraInfo: CameraInfo,
        extensionMode: ExtensionMode,
    ): Boolean {
        return try {
            val camera2Info = Camera2CameraInfo.from(cameraInfo)
            val exposureModes = camera2Info
                .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
                ?: intArrayOf()
            val requestKeys = cameraManager
                .getCameraExtensionCharacteristics(camera2Info.cameraId)
                .getAvailableCaptureRequestKeys(camera2Extension(extensionMode))
            val takesExposureMode = CaptureRequest.CONTROL_AE_MODE in requestKeys
            val offersLowLightBoost = CameraMetadata
                .CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY in exposureModes

            takesExposureMode && offersLowLightBoost
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Extension $extensionMode has no characteristics to check", exception)
            false
        } catch (exception: CameraAccessException) {
            Log.w(TAG, "Camera is not reachable to check extension $extensionMode", exception)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun camera2Extension(extensionMode: ExtensionMode): Int {
        return when (extensionMode) {
            ExtensionMode.AUTO -> CameraExtensionCharacteristics.EXTENSION_AUTOMATIC
            ExtensionMode.FACE_RETOUCH -> CameraExtensionCharacteristics.EXTENSION_FACE_RETOUCH
            ExtensionMode.BOKEH -> CameraExtensionCharacteristics.EXTENSION_BOKEH
            ExtensionMode.NIGHT -> CameraExtensionCharacteristics.EXTENSION_NIGHT
            ExtensionMode.HDR -> CameraExtensionCharacteristics.EXTENSION_HDR
        }
    }

    private companion object {
        const val TAG = "LowLightBoostSupport"
    }
}
