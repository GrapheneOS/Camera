package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.ui.viewfinder.screen.model.LevelUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import javax.inject.Inject
import kotlin.math.abs

interface LevelUiStateMapper {

    fun map(
        current: LevelUiState,
        state: ViewfinderState,
        motion: DeviceMotion,
        previousMotion: DeviceMotion?,
    ): LevelUiState
}

internal class LevelUiStateMapperImpl @Inject constructor() : LevelUiStateMapper {

    override fun map(
        current: LevelUiState,
        state: ViewfinderState,
        motion: DeviceMotion,
        previousMotion: DeviceMotion?,
    ): LevelUiState {
        val visible = current.visible || isSteadyNearLevel(
            previousMotion = previousMotion,
            motion = motion,
        )

        return when {
            !state.showsLevel() || isBeyondLevelRange(motion) -> {
                current.copy(visible = false)
            }

            state.capture.isSelfTimerRunning -> {
                current.copy(visible = visible)
            }

            else -> {
                LevelUiState(
                    visible = visible,
                    tiltDegrees = motion.tiltDegrees,
                    horizonDegrees = motion.horizonDegrees,
                )
            }
        }
    }

    private fun isBeyondLevelRange(motion: DeviceMotion): Boolean {
        return abs(motion.tiltDegrees) > LEVEL_RANGE_DEGREES ||
            abs(motion.horizonDegrees) > LEVEL_RANGE_DEGREES
    }

    private fun isSteadyNearLevel(
        previousMotion: DeviceMotion?,
        motion: DeviceMotion,
    ): Boolean {
        val previousTilt = previousMotion?.tiltDegrees ?: 0
        val previousHorizon = previousMotion?.horizonDegrees ?: 0

        return abs(motion.tiltDegrees) <= TILT_ENTRY_DEGREES &&
            abs(motion.horizonDegrees) <= HORIZON_ENTRY_DEGREES &&
            abs(motion.tiltDegrees - previousTilt) < STEADY_DEGREES &&
            abs(motion.horizonDegrees - previousHorizon) < STEADY_DEGREES
    }

    private companion object {
        private const val LEVEL_RANGE_DEGREES = 45
        private const val TILT_ENTRY_DEGREES = 8
        private const val HORIZON_ENTRY_DEGREES = 25
        private const val STEADY_DEGREES = 5
    }
}
