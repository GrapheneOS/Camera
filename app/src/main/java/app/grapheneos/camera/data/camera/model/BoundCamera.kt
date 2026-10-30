package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.core.model.VideoQuality

data class BoundCamera(
    val mode: CameraMode,
    val isVideoMode: Boolean,
    val lensFacing: LensFacing,
    val hasFlashUnit: Boolean,
    val videoQualities: List<VideoQuality>,
    val highFrameRate: HighFrameRateSupport? = null,
) {

    fun modeSlot(): ModeSlot {
        return ModeSlot(
            mode = mode,
            isFrontFacing = lensFacing == LensFacing.FRONT,
        )
    }

    fun recordedVideoQuality(stored: VideoQuality): VideoQuality {
        // CameraX lists the supported qualities from the highest down.
        return when (stored) {
            VideoQuality.HIGHEST -> videoQualities.firstOrNull() ?: stored
            else -> stored
        }
    }
}
