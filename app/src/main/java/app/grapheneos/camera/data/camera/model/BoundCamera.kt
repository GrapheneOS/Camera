package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.core.model.VideoQuality

data class BoundCamera(
    val mode: CameraMode,
    val isVideoMode: Boolean,
    val lensFacing: LensFacing,
    val hasFlashUnit: Boolean,
    val videoQualities: List<VideoQuality>,
    val isLowLightBoostSupported: Boolean = false,
    val highFrameRate: HighFrameRateSupport? = null,
) {

    fun modeSlot(): ModeSlot {
        return ModeSlot(
            mode = mode,
            lensFacing = lensFacing,
        )
    }

    fun flashModes(): List<FlashMode> {
        return buildList {
            add(FlashMode.OFF)
            if (hasFlashUnit && !isVideoMode) {
                add(FlashMode.AUTO)
            }
            if (hasFlashUnit) {
                add(FlashMode.ON)
            }
            if (lensFacing == LensFacing.FRONT) {
                add(FlashMode.SELF_ILLUMINATION)
            }
            if (isLowLightBoostSupported) {
                add(FlashMode.LOW_LIGHT_BOOST)
            }
        }
    }
}
