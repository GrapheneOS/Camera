package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode

data class CameraBindSettings(
    val mode: CameraMode,
    val aspectRatio: AspectRatio,
    val photo: PhotoBindSettings?,
    val video: VideoBindSettings?,
    val qr: QrBindSettings?,
)
