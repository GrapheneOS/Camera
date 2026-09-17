package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality

data class CameraBindSettings(
    val mode: CameraMode,
    val isQrMode: Boolean,
    val isVideoMode: Boolean,
    val requiresVideoModeOnly: Boolean,
    val qrLensFacing: LensFacing?,
    val rotation: Int,
    val aspectRatio: AspectRatio,
    val flashMode: FlashMode,
    val photoQuality: Int,
    val videoQuality: VideoQuality,
    val waitForFocusLock: Boolean,
    val enableZsl: Boolean,
    val enableEis: Boolean,
    val selectHighestResolution: Boolean,
    val mirrorVideoOnFrontCamera: Boolean,
)
