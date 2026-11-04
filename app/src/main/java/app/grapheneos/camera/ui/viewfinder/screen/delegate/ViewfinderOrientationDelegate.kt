package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepository
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface ViewfinderOrientationDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    fun setDisplayRotation(rotation: Int)

    suspend fun trackOrientation()
}

internal class ViewfinderOrientationDelegateImpl @Inject constructor(
    private val deviceOrientationRepository: DeviceOrientationRepository,
) : ViewfinderOrientationDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder
    }

    override fun setDisplayRotation(rotation: Int) {
        stateHolder.update { state -> state.copy(displayRotation = rotation) }
    }

    override suspend fun trackOrientation() {
        coroutineScope {
            launch { trackMotion() }
            launch { trackAutoRotate() }
        }
    }

    private suspend fun trackMotion() {
        deviceOrientationRepository.motion
            .map { motion -> motion.orientation }
            .distinctUntilChanged()
            .collect { orientation ->
                stateHolder.update { state ->
                    state.copy(deviceOrientation = orientation)
                }
            }
    }

    private suspend fun trackAutoRotate() {
        deviceOrientationRepository.autoRotateEnabled().collect { enabled ->
            stateHolder.update { state ->
                state.copy(autoRotateEnabled = enabled)
            }
        }
    }
}
