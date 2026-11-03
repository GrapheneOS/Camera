package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.core.model.VideoFrameRate
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
            if (isLowLightBoostSupported) {
                add(FlashMode.LOW_LIGHT_BOOST)
            }
        }
    }

    // A stored mode this camera doesn't offer, or can't use right now, leaves the flash off.
    fun appliedFlashMode(
        stored: FlashMode,
        frameRate: VideoFrameRate,
    ): FlashMode {
        val isApplied = stored in flashModes() &&
            (stored != FlashMode.LOW_LIGHT_BOOST || !limitsLowLightBoost(frameRate))

        return when {
            isApplied -> stored
            else -> FlashMode.OFF
        }
    }

    // CameraX turns low light boost off above 30 fps.
    fun limitsLowLightBoost(frameRate: VideoFrameRate): Boolean {
        return isLowLightBoostSupported &&
            isVideoMode &&
            frameRate == VideoFrameRate.FPS_60 &&
            highFrameRate == HighFrameRateSupport.Supported
    }

    fun recordedVideoQuality(stored: VideoQuality): VideoQuality {
        // CameraX lists the supported qualities from the highest down.
        return when (stored) {
            VideoQuality.HIGHEST -> videoQualities.firstOrNull() ?: stored
            else -> stored
        }
    }
}
