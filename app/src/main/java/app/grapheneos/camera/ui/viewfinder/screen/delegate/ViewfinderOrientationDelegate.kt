package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepository
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.mapper.LevelUiStateMapper
import app.grapheneos.camera.ui.viewfinder.screen.model.LevelUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface ViewfinderOrientationDelegate {

    val levelUiState: StateFlow<LevelUiState>

    fun bind(stateHolder: ViewfinderStateHolder)

    fun setDisplayRotation(rotation: Int)

    suspend fun trackOrientation()
}

internal class ViewfinderOrientationDelegateImpl @Inject constructor(
    private val deviceOrientationRepository: DeviceOrientationRepository,
    private val levelUiStateMapper: LevelUiStateMapper,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderOrientationDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    private val _levelUiState = MutableStateFlow(LevelUiState())
    override val levelUiState: StateFlow<LevelUiState> = _levelUiState.asStateFlow()

    override fun bind(stateHolder: ViewfinderStateHolder) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder
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

            _levelUiState.value = levelUiStateMapper.map(
                current = _levelUiState.value,
                previousMotion = previousMotion,
                motion = motion,
                state = stateHolder.state.value,
            )
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
                _levelUiState.update { level ->
                    level.copy(visible = false)
                }
            }
    }

    private suspend fun announceLevelReached() {
        var isArmed = true
        var isAnnounced = false

        _levelUiState
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
