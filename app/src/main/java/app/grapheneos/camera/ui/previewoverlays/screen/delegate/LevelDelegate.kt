package app.grapheneos.camera.ui.previewoverlays.screen.delegate

import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepository
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.mapper.LevelStateMapper
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelPose
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysState
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

    suspend fun trackLevel()
}

internal class LevelDelegateImpl @Inject constructor(
    private val deviceOrientationRepository: DeviceOrientationRepository,
    private val levelStateMapper: LevelStateMapper,
) : LevelDelegate {

    private lateinit var stateHolder: StateHolder<PreviewOverlaysState>

    override fun bind(stateHolder: StateHolder<PreviewOverlaysState>) {
        this.stateHolder = stateHolder
    }

    override suspend fun trackLevel() {
        stateHolder.state
            .map { state ->
                when {
                    state.canShowLevel() -> state.previewContent?.mode
                    else -> null
                }
            }
            .distinctUntilChanged()
            .collectLatest { mode ->
                stateHolder.update { it.resetLevel() }

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
                val state = stateHolder.state.value
                val previousPose = state.level.pose
                val level = publishMotion(state = state, motion = motion)
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

    private fun publishMotion(
        state: PreviewOverlaysState,
        motion: DeviceMotion,
    ): LevelState {
        val level = levelStateMapper.map(state = state, motion = motion)
        val published = when {
            level.visible -> level
            else -> level.copy(
                tiltDegrees = state.level.tiltDegrees,
                horizonDegrees = state.level.horizonDegrees,
            )
        }

        if (published != state.level) {
            stateHolder.update { it.copy(level = published) }
        }

        return level
    }

    private fun revealLevel(
        motion: DeviceMotion,
        expectedPose: LevelPose,
    ) {
        stateHolder.update { state ->
            val candidate = levelStateMapper.map(state = state, motion = motion)
            when {
                state.level.pose != expectedPose || candidate.pose != expectedPose -> state
                else -> state.copy(level = candidate.copy(visible = true))
            }
        }
    }

    private companion object {
        private val REVEAL_DELAY = 1.seconds
    }
}
