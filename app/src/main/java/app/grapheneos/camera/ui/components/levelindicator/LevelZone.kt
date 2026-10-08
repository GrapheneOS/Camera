package app.grapheneos.camera.ui.components.levelindicator

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
        private const val TOP_DOWN_ENTER_DEGREES = 60f
        private const val TOP_DOWN_EXIT_DEGREES = 50f
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

        fun isTopDown(
            pitch: Float,
            wasTopDown: Boolean,
        ): Boolean {
            return when {
                abs(pitch) >= TOP_DOWN_ENTER_DEGREES -> true
                abs(pitch) <= TOP_DOWN_EXIT_DEGREES -> false
                else -> wasTopDown
            }
        }

        fun of(
            roll: Float,
            pitch: Float,
        ): LevelZone {
            val isLevel = isVertical(pitch = pitch) ||
                (isRollLevel(roll = roll) && isPitchLevel(pitch = pitch))
            val isNear = tiltFromVertical(pitch = pitch) < AWAY_DEGREES ||
                (abs(roll) < AWAY_DEGREES && abs(pitch) < AWAY_DEGREES)

            return when {
                isLevel -> Level
                isNear -> Near
                else -> Away
            }
        }
    }
}
