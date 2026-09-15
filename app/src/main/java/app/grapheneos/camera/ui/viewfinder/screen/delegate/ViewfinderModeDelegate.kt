package app.grapheneos.camera.ui.viewfinder.screen.delegate

import androidx.camera.core.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import javax.inject.Inject

interface ViewfinderModeDelegate {
    val defaultMode: CameraMode
    val currentMode: CameraMode
    val isQrMode: Boolean
    val isVideoMode: Boolean
    val isInPhotoMode: Boolean

    fun bind(stateHolder: ViewfinderStateHolder)

    fun select(mode: CameraMode): Boolean
    fun aspectRatio(storedAspectRatio: Int): Int
}

internal class ViewfinderModeDelegateImpl @Inject constructor(
    private val entryPoint: CameraEntryPoint,
) : ViewfinderModeDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    override val defaultMode: CameraMode
        get() {
            return DEFAULT_MODE
        }

    override val currentMode: CameraMode
        get() {
            return stateHolder.state.value.mode
        }

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

    override fun bind(stateHolder: ViewfinderStateHolder) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder
    }

    override fun select(mode: CameraMode): Boolean {
        if (currentMode == mode) return false

        stateHolder.update { it.copy(mode = mode) }

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
