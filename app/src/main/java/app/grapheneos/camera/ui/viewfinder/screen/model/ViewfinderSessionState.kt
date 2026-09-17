package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.camera.model.LensFacing

data class ViewfinderSessionState(
    val lensFacing: LensFacing = LensFacing.BACK,
    val canTakePicture: Boolean = false,
    val isFlashAvailable: Boolean = false,
    val canApplyVideoStabilization: Boolean = false,
    val isTorchOn: Boolean = false,
)
