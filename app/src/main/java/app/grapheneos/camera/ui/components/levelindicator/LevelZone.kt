package app.grapheneos.camera.ui.components.levelindicator

import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import kotlin.math.abs

internal enum class LevelZone {
    Level,
    Near,
    Away,
    ;

    companion object {
        private const val LEVEL_ROLL_DEGREES = 0.5f
        private const val LEVEL_DEGREES = 1f
        private const val AWAY_DEGREES = 2f
        private const val VERTICAL_DEGREES = 90f

        fun isRollLevel(roll: Float): Boolean {
            return abs(roll) < LEVEL_ROLL_DEGREES
        }

        fun isPitchLevel(pitch: Float): Boolean {
            return abs(pitch) < LEVEL_DEGREES
        }

        fun isVertical(pitch: Float): Boolean {
            return tiltFromVertical(pitch = pitch) < LEVEL_DEGREES
        }

        fun tiltFromVertical(pitch: Float): Float {
            return VERTICAL_DEGREES - abs(pitch)
        }

        fun of(
            mode: LevelIndicatorMode,
            roll: Float,
            pitch: Float,
        ): LevelZone {
            val isLevel = when (mode) {
                LevelIndicatorMode.Horizon -> isRollLevel(roll) && isPitchLevel(pitch)
                LevelIndicatorMode.Crosshairs -> isVertical(pitch)
            }
            val isNear = when (mode) {
                LevelIndicatorMode.Horizon -> abs(roll) < AWAY_DEGREES && abs(pitch) < AWAY_DEGREES
                LevelIndicatorMode.Crosshairs -> tiltFromVertical(pitch) < AWAY_DEGREES
            }

            return when {
                isLevel -> Level
                isNear -> Near
                else -> Away
            }
        }
    }
}
