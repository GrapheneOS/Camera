package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepository
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderLevelStateMapper
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderLevelState
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface ViewfinderOrientationDelegate {

    fun bind(
        stateHolder: ViewfinderStateHolder,
        levelStateHolder: StateHolder<ViewfinderLevelState>,
    )

    fun setDisplayRotation(rotation: Int)

    suspend fun trackOrientation()
}

internal class ViewfinderOrientationDelegateImpl @Inject constructor(
    private val deviceOrientationRepository: DeviceOrientationRepository,
    private val levelStateMapper: ViewfinderLevelStateMapper,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderOrientationDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private lateinit var levelStateHolder: StateHolder<ViewfinderLevelState>

    override fun bind(
        stateHolder: ViewfinderStateHolder,
        levelStateHolder: StateHolder<ViewfinderLevelState>,
    ) {
        this.stateHolder = stateHolder
        this.levelStateHolder = levelStateHolder
    }

    override fun setDisplayRotation(rotation: Int) {
        stateHolder.update { state ->
            state.copy(displayRotation = rotation)
        }
    }

    override suspend fun trackOrientation() {
        coroutineScope {
            launch(mainDispatcher) { trackMotion() }
            launch(mainDispatcher) { trackAutoRotate() }
            launch(mainDispatcher) { hideLevelOutsidePhotoMode() }
            launch(mainDispatcher) { announceLevelReached() }
        }
    }

    private suspend fun trackMotion() {
        var previousMotion: DeviceMotion? = null

        deviceOrientationRepository.motion().collect { motion ->
            stateHolder.update { state ->
                state.copy(deviceOrientation = motion.orientation)
            }

            levelStateHolder.update { current ->
                levelStateMapper.map(
                    current = current,
                    previousMotion = previousMotion,
                    motion = motion,
                    state = stateHolder.state.value,
                )
            }
            previousMotion = motion
        }
    }

    private suspend fun trackAutoRotate() {
        deviceOrientationRepository.autoRotateEnabled().collect { enabled ->
            stateHolder.update { state ->
                state.copy(autoRotateEnabled = enabled)
            }
        }
    }

    private suspend fun hideLevelOutsidePhotoMode() {
        stateHolder.state
            .map { state -> state.showsLevel() }
            .distinctUntilChanged()
            .filterNot { shown -> shown }
            .collect {
                levelStateHolder.update { level ->
                    level.copy(visible = false)
                }
            }
    }

    private suspend fun announceLevelReached() {
        var isArmed = true
        var isAnnounced = false

        levelStateHolder.state
            .map { level -> level.tiltDegrees }
            .distinctUntilChanged()
            .drop(1)
            .collectLatest { tilt ->
                val isLevel = tilt == 0
                val hasTiltedAway = !isAnnounced || abs(tilt) > REARM_TILT_DEGREES

                when {
                    isLevel && isArmed -> {
                        isArmed = false
                        isAnnounced = false
                        delay(LEVEL_HOLD)
                        isAnnounced = true
                        stateHolder.postEffect(Effect.PlayLevelHaptic)
                    }

                    !isLevel && hasTiltedAway -> {
                        isArmed = true
                    }
                }
            }
    }

    private companion object {
        private const val REARM_TILT_DEGREES = 5

        private val LEVEL_HOLD = 250.milliseconds
    }
}
