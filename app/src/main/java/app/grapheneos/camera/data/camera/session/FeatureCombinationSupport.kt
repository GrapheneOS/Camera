package app.grapheneos.camera.data.camera.session

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import javax.inject.Inject

// Whether CameraX's feature-group resolution can actually *verify* feature combinations on
// the current camera. The resolver (DefaultFeatureGroupResolver) keeps a candidate
// combination only when the platform confirms it, and it treats "could not check" exactly
// like "verified unsupported": below API 35 CameraX substitutes a no-op feature-combination
// query whose isSupported() is unconditionally false (CameraSurfaceAdapter), and on API 35+
// a HAL that does not implement the session-configuration query answers UNKNOWN, which is
// folded into false as well (camera-pipe ConfigQueryResult). On such a camera, combinations
// the device records fine every day resolve down to little or nothing: the stored video
// quality is silently ignored (setQualitySelector must not be called while a feature group
// is in use, see startCamera) and the divergence notice asserts unsupportedness that was
// never actually verified. So the feature-group path is only taken when the platform can
// genuinely answer, and startCamera otherwise falls back to the pre-1.6 configuration APIs,
// which never drop the chosen quality.
interface FeatureCombinationSupport {
    fun canVerify(cameraInfo: CameraInfo): Boolean
}

internal class FeatureCombinationSupportImpl @Inject constructor() : FeatureCombinationSupport {

    // The per-camera INFO_SESSION_CONFIGURATION_QUERY_VERSION characteristic is what the
    // platform's answers ultimately hinge on (CameraDeviceSetup exists only for cameras
    // reporting >= 35 there), so it is checked in addition to the API level. This deliberately
    // mirrors CameraX's own SDK_INT >= 35 gate — which upstream may still move, see b/417839748
    // — plus the HAL capability that gate cannot see; re-check both on CameraX updates.
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    override fun canVerify(cameraInfo: CameraInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false

        val queryVersion = try {
            Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(
                CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION
            )
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Camera info carries no camera2 characteristics", exception)
            null
        }

        return (queryVersion ?: 0) >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    }

    private companion object {
        const val TAG = "FeatureCombination"
    }
}
