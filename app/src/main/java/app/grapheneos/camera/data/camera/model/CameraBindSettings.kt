package app.grapheneos.camera.data.camera.model

import androidx.camera.video.Quality
import app.grapheneos.camera.data.core.model.CameraMode

data class CameraBindSettings(
    val mode: CameraMode,
    val isQrMode: Boolean,
    val isVideoMode: Boolean,
    val requiresVideoModeOnly: Boolean,
    val qrLensFacing: Int?,
    val rotation: Int,
    val aspectRatio: Int,
    val flashMode: Int,
    val photoQuality: Int,
    val videoQuality: Quality,
    val waitForFocusLock: Boolean,
    val enableZsl: Boolean,
    val enableEis: Boolean,
    val selectHighestResolution: Boolean,
    val mirrorVideoOnFrontCamera: Boolean,
)
