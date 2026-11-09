package app.grapheneos.camera.ui.previewoverlays.screen.delegate

import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepository
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.mapper.LevelStateMapper
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.LevelPose
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.LevelState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal interface LevelDelegate {

    fun bind(stateHolder: StateHolder<PreviewOverlaysState>)

    fun levelAfterChange(
        previous: PreviewOverlaysState,
        current: PreviewOverlaysState,
    ): LevelState

    fun onScreenPaused()

    suspend fun trackLevel()
}

internal class LevelDelegateImpl @Inject constructor(
    private val deviceOrientationRepository: DeviceOrientationRepository,
    private val levelStateMapper: LevelStateMapper,
) : LevelDelegate {

    private lateinit var stateHolder: StateHolder<PreviewOverlaysState>

    private val state: PreviewOverlaysState
        get() = stateHolder.state.value

    override fun bind(stateHolder: StateHolder<PreviewOverlaysState>) {
        this.stateHolder = stateHolder
    }

    override fun levelAfterChange(
        previous: PreviewOverlaysState,
        current: PreviewOverlaysState,
    ): LevelState {
        val modeChanged = previous.previewContent?.mode != current.previewContent?.mode
        return when {
            current.isLevelAvailable() && !modeChanged -> current.level
            else -> LevelState()
        }
    }

    override fun onScreenPaused() {
        resetLevel()
    }

    override suspend fun trackLevel() {
        stateHolder.state
            .map { state ->
                when {
                    state.isLevelAvailable() -> state.previewContent?.mode
                    else -> null
                }
            }
            .distinctUntilChanged()
            .collectLatest { mode ->
                resetLevel()

                if (mode != null) {
                    trackMotion()
                }
            }
    }

    private suspend fun trackMotion() {
        coroutineScope {
            var latestMotion: DeviceMotion
            var pendingReveal: Job? = null

            deviceOrientationRepository.motion.collect { motion ->
                latestMotion = motion
                val previousPose = state.level.pose
                val level = publishMotion(motion)
                val pose = level.pose
                val poseChanged = previousPose != pose
                val isRevealPending = pendingReveal?.isActive == true

                when {
                    pose == null || level.visible -> {
                        pendingReveal?.cancel()
                        pendingReveal = null
                    }

                    poseChanged || !isRevealPending -> {
                        pendingReveal?.cancel()
                        pendingReveal = this@coroutineScope.launch {
                            delay(REVEAL_DELAY)
                            revealLevel(
                                motion = latestMotion,
                                expectedPose = pose,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun publishMotion(motion: DeviceMotion): LevelState {
        val level = levelStateMapper.map(
            state = state,
            motion = motion,
        )
        val published = when {
            level.visible -> level
            else -> level.copy(
                tiltDegrees = state.level.tiltDegrees,
                horizonDegrees = state.level.horizonDegrees,
            )
        }

        stateHolder.update { state -> state.copy(level = published) }

        return level
    }

    private fun revealLevel(
        motion: DeviceMotion,
        expectedPose: LevelPose,
    ) {
        stateHolder.update { state ->
            val candidate = levelStateMapper.map(
                state = state,
                motion = motion,
            )
            when {
                state.level.pose != expectedPose || candidate.pose != expectedPose -> state
                else -> state.copy(level = candidate.copy(visible = true))
            }
        }
    }

    private fun resetLevel() {
        stateHolder.update { state -> state.copy(level = LevelState()) }
    }

    private companion object {
        private val REVEAL_DELAY = 1.seconds
    }
}
