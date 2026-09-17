package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality

data class ViewfinderSessionState(
    val lensFacing: LensFacing = LensFacing.BACK,
    val canTakePicture: Boolean = false,
    val isFlashAvailable: Boolean = false,
    val canApplyVideoStabilization: Boolean = false,
    val isTorchOn: Boolean = false,
    val isQrResultShown: Boolean = false,
    val isZslSupported: Boolean = false,
    val sensorOrientationDegrees: Int? = null,
    val zoom: CameraZoom? = null,
    val exposure: CameraExposure? = null,
    val videoQualities: List<VideoQuality> = emptyList(),
    val availableModes: Set<CameraMode> = emptySet(),
)
