package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.camera.session.ExposureControl
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import javax.inject.Inject

interface ViewfinderCameraControlsDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    fun setExposureCompensation(compensationIndex: Int)
}

internal class ViewfinderCameraControlsDelegateImpl @Inject constructor(
    private val exposureControl: ExposureControl,
) : ViewfinderCameraControlsDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder
    }

    override fun setExposureCompensation(compensationIndex: Int) {
        exposureControl.setCompensationIndex(compensationIndex)

        stateHolder.update {
            val exposure = it.session.exposure?.copy(compensationIndex = compensationIndex)

            it.copy(session = it.session.copy(exposure = exposure))
        }
    }
}
