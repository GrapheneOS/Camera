package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.FlashMode

data class CameraFlash(
    val selectedMode: FlashMode,
    val unavailableModes: Set<FlashMode>,
) {

    fun isSelfIlluminating(): Boolean {
        return selectedMode == FlashMode.SELF_ILLUMINATION
    }
}
