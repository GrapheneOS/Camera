package app.grapheneos.camera.ui.components.levelindicator

import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import app.grapheneos.camera.ui.components.motion.shortestTurn
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin
import kotlin.time.Duration

internal class LevelIndicatorFilter(
    private val mode: LevelIndicatorMode,
) {

    private val horizontalAngleFilter = LevelAngleFilter(
        wrapsAtFullTurn = mode == LevelIndicatorMode.Horizon,
    )
    private val verticalAngleFilter = LevelAngleFilter(
        wrapsAtFullTurn = false,
    )

    val isSettled: Boolean
        get() {
            return horizontalAngleFilter.isSettled && verticalAngleFilter.isSettled
        }

    fun update(
        roll: Float,
        pitch: Float,
        elapsed: Duration,
    ): Angles {
        return when (mode) {
            LevelIndicatorMode.Horizon -> filterHorizon(
                roll = roll,
                pitch = pitch,
                elapsed = elapsed,
            )

            LevelIndicatorMode.Crosshairs -> filterCrosshairTilt(
                roll = roll,
                pitch = pitch,
                elapsed = elapsed,
            )
        }
    }

    private fun filterHorizon(
        roll: Float,
        pitch: Float,
        elapsed: Duration,
    ): Angles {
        val alignedRoll = when {
            LevelZone.isRollLevel(shortestTurn(from = 0f, to = roll)) -> 0f
            else -> roll
        }
        val alignedPitch = when {
            LevelZone.isPitchLevel(pitch) -> 0f
            else -> pitch
        }

        return Angles(
            roll = horizontalAngleFilter.update(degrees = alignedRoll, elapsed = elapsed),
            pitch = verticalAngleFilter.update(degrees = alignedPitch, elapsed = elapsed),
        )
    }

    private fun filterCrosshairTilt(
        roll: Float,
        pitch: Float,
        elapsed: Duration,
    ): Angles {
        val tiltDegrees = LevelZone.tiltFromVertical(pitch)
        val tiltMagnitude = when {
            tiltDegrees < VERTICAL_SNAP_DEGREES -> 0f
            else -> tiltDegrees
        }
        val rollRadians = Math.toRadians(roll.toDouble())
        // Near vertical, azimuth is unstable even when the projected tilt barely moves.
        val horizontalTilt = horizontalAngleFilter.update(
            degrees = tiltMagnitude * sin(rollRadians).toFloat(),
            elapsed = elapsed,
        )
        val verticalTilt = verticalAngleFilter.update(
            degrees = tiltMagnitude * cos(rollRadians).toFloat(),
            elapsed = elapsed,
        )
        val filteredRollRadians = atan2(horizontalTilt.toDouble(), verticalTilt.toDouble())

        return Angles(
            roll = Math.toDegrees(filteredRollRadians).toFloat(),
            pitch = sign(pitch) * (VERTICAL_DEGREES - hypot(horizontalTilt, verticalTilt)),
        )
    }

    data class Angles(
        val roll: Float,
        val pitch: Float,
    )

    private companion object {
        private const val VERTICAL_DEGREES = 90f
        private const val VERTICAL_SNAP_DEGREES = 0.15f
    }
}
