package app.grapheneos.camera.ui.components.levelindicator

import kotlin.math.abs

internal enum class LevelZone {
    Level,
    Near,
    Away,
    ;

    companion object {
        private const val LEVEL_ROLL_DEGREES = 0.5f
        private const val LEVEL_PITCH_DEGREES = 1f
        private const val AWAY_DEGREES = 2f

        fun isRollLevel(roll: Float): Boolean {
            return abs(roll) < LEVEL_ROLL_DEGREES
        }

        fun isPitchLevel(pitch: Float): Boolean {
            return abs(pitch) < LEVEL_PITCH_DEGREES
        }

        fun of(
            roll: Float,
            pitch: Float,
        ): LevelZone {
            return when {
                isRollLevel(roll = roll) && isPitchLevel(pitch = pitch) -> Level
                abs(roll) < AWAY_DEGREES && abs(pitch) < AWAY_DEGREES -> Near
                else -> Away
            }
        }
    }
}
