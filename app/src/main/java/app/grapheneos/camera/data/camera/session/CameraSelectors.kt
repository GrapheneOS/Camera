package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraSelector
import app.grapheneos.camera.data.camera.model.LensFacing

private val FRONT_CAMERA_SELECTOR: CameraSelector = CameraSelector.Builder()
    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
    .build()

private val REAR_CAMERA_SELECTOR: CameraSelector = CameraSelector.Builder()
    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
    .build()

internal fun cameraSelectorFor(lensFacing: LensFacing): CameraSelector {
    return when (lensFacing) {
        LensFacing.FRONT -> FRONT_CAMERA_SELECTOR
        LensFacing.BACK -> REAR_CAMERA_SELECTOR
    }
}
