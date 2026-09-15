package app.grapheneos.camera.ui.viewfinder.screen.delegate

import androidx.camera.core.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import javax.inject.Inject

interface ViewfinderModeDelegate {
    val defaultMode: CameraMode
    val currentMode: CameraMode
    val isQrMode: Boolean
    val isVideoMode: Boolean
    val isInPhotoMode: Boolean

    fun select(mode: CameraMode): Boolean
    fun aspectRatio(storedAspectRatio: Int): Int
}

internal class ViewfinderModeDelegateImpl @Inject constructor(
    private val entryPoint: CameraEntryPoint,
) : ViewfinderModeDelegate {

    override val defaultMode: CameraMode
        get() {
            return DEFAULT_MODE
        }

    override var currentMode: CameraMode = DEFAULT_MODE
        private set

    override val isQrMode: Boolean
        get() {
            return currentMode.isQr
        }

    override val isVideoMode: Boolean
        get() {
            return currentMode.isVideo || entryPoint.requiresVideoModeOnly
        }

    override val isInPhotoMode: Boolean
        get() {
            return !(isQrMode || isVideoMode)
        }

    override fun select(mode: CameraMode): Boolean {
        if (currentMode == mode) return false

        currentMode = mode

        return true
    }

    override fun aspectRatio(storedAspectRatio: Int): Int {
        return when {
            isVideoMode -> AspectRatio.RATIO_16_9
            isQrMode -> AspectRatio.RATIO_4_3
            else -> storedAspectRatio
        }
    }

    private companion object {
        val DEFAULT_MODE = CameraMode.CAMERA
    }
}
