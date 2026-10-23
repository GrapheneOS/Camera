package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import javax.inject.Inject

interface ViewfinderModeDelegate {
    val defaultMode: CameraMode

    fun bind(stateHolder: ViewfinderStateHolder)

    fun select(mode: CameraMode): Boolean
}

internal class ViewfinderModeDelegateImpl @Inject constructor() : ViewfinderModeDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    override val defaultMode: CameraMode
        get() {
            return DEFAULT_MODE
        }

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder
    }

    override fun select(mode: CameraMode): Boolean {
        if (stateHolder.state.value.mode == mode) return false

        stateHolder.update { it.copy(mode = mode) }

        return true
    }

    private companion object {
        val DEFAULT_MODE = CameraMode.CAMERA
    }
}
