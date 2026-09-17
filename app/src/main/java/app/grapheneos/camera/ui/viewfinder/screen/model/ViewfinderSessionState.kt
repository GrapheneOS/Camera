package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.camera.core.CameraSelector

data class ViewfinderSessionState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val canTakePicture: Boolean = false,
    val isFlashAvailable: Boolean = false,
    val canApplyVideoStabilization: Boolean = false,
    val isTorchOn: Boolean = false,
)
