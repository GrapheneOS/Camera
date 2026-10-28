package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality

data class BoundCamera(
    val mode: CameraMode,
    val isVideoMode: Boolean,
    val lensFacing: LensFacing,
    val hasFlashUnit: Boolean,
    val videoQualities: List<VideoQuality>,
)
