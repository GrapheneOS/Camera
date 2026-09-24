package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepository
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import javax.inject.Inject

interface ViewfinderOrientationDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    suspend fun trackOrientation()
}

internal class ViewfinderOrientationDelegateImpl @Inject constructor(
    private val deviceOrientationRepository: DeviceOrientationRepository,
) : ViewfinderOrientationDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    override fun bind(stateHolder: ViewfinderStateHolder) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder
    }

    override suspend fun trackOrientation() {
        deviceOrientationRepository.orientation().collect { orientation ->
            stateHolder.update { state ->
                state.copy(deviceOrientation = orientation)
            }
        }
    }
}
