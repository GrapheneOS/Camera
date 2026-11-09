package app.grapheneos.camera.ui.previewoverlays.screen.mapper

import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.LevelPose
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.LevelState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import javax.inject.Inject
import kotlin.math.abs

internal interface LevelStateMapper {

    fun map(
        state: PreviewOverlaysState,
        motion: DeviceMotion,
    ): LevelState
}

internal class LevelStateMapperImpl @Inject constructor() : LevelStateMapper {

    override fun map(
        state: PreviewOverlaysState,
        motion: DeviceMotion,
    ): LevelState {
        val current = state.level
        val pose = when {
            state.isLevelAvailable() -> poseOf(
                current = current,
                motion = motion,
            )
            else -> null
        }
        val visible = current.visible && pose != null && pose == current.pose

        return current.copy(
            visible = visible,
            pose = pose,
            tiltDegrees = motion.tiltDegrees,
            horizonDegrees = motion.horizonDegrees,
            orientation = motion.orientation,
        )
    }

    private fun poseOf(
        current: LevelState,
        motion: DeviceMotion,
    ): LevelPose? {
        val isHorizonVisible = current.visible && current.pose == LevelPose.Horizon
        val rollLimit = when {
            isHorizonVisible -> HORIZON_EXIT_DEGREES
            else -> HORIZON_ROLL_ENTRY_DEGREES
        }
        val pitchLimit = when {
            isHorizonVisible -> HORIZON_EXIT_DEGREES
            else -> HORIZON_PITCH_ENTRY_DEGREES
        }
        return when {
            motion.horizonDegrees >= VERTICAL_ENTRY_DEGREES -> LevelPose.FacingDown
            motion.horizonDegrees <= -VERTICAL_ENTRY_DEGREES -> LevelPose.FacingUp
            abs(motion.tiltDegrees) <= rollLimit &&
                abs(motion.horizonDegrees) <= pitchLimit -> LevelPose.Horizon
            else -> null
        }
    }

    private companion object {
        private const val HORIZON_ROLL_ENTRY_DEGREES = 8f
        private const val HORIZON_PITCH_ENTRY_DEGREES = 25f
        private const val HORIZON_EXIT_DEGREES = 45f
        private const val VERTICAL_ENTRY_DEGREES = 75f
    }
}
