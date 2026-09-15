package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import javax.inject.Inject

interface ViewfinderModeDelegate {
    val defaultMode: CameraMode
    val currentMode: CameraMode

    fun bind(stateHolder: ViewfinderStateHolder)

    fun select(mode: CameraMode): Boolean
}

internal class ViewfinderModeDelegateImpl @Inject constructor() : ViewfinderModeDelegate {

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

    private companion object {
        val DEFAULT_MODE = CameraMode.CAMERA
    }
}
