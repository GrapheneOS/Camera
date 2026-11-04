package app.grapheneos.camera.ui.components.levelindicator

import app.grapheneos.camera.ui.components.motion.shortestTurn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class LevelAngleFilter(
    private val wrapsAtFullTurn: Boolean,
) {

    private var acceptedDegrees: Float? = null
    private var filteredDegrees: Float? = null
    private var previousInputDegrees: Float = 0f
    private var speedDegreesPerSecond: Float = 0f

    var isSettled: Boolean = true
        private set

    fun update(
        degrees: Float,
        elapsed: Duration,
    ): Float {
        val reading = acceptReading(degrees)
        val previous = filteredDegrees
        if (previous == null) {
            filteredDegrees = reading
            previousInputDegrees = reading
            return reading
        }

        val elapsedSeconds = (elapsed.coerceAtMost(MAXIMUM_FRAME_INTERVAL) / 1.seconds).toFloat()
        val cutoff = updateCutoff(
            reading = reading,
            elapsedSeconds = elapsedSeconds,
        )
        val positionWeight = smoothingWeight(
            cutoff = cutoff,
            elapsedSeconds = elapsedSeconds,
        )
        return updatePosition(
            current = previous,
            reading = reading,
            weight = positionWeight,
        )
    }

    private fun acceptReading(degrees: Float): Float {
        val normalizedDegrees = when {
            !wrapsAtFullTurn -> degrees
            degrees >= -HALF_TURN_DEGREES && degrees <= HALF_TURN_DEGREES -> degrees
            else -> shortestTurn(from = 0f, to = degrees)
        }
        val previous = acceptedDegrees ?: normalizedDegrees
        val movementDegrees = abs(difference(from = previous, to = normalizedDegrees))
        val accepted = when {
            movementDegrees >= MOVEMENT_THRESHOLD_DEGREES -> normalizedDegrees
            else -> previous
        }
        acceptedDegrees = accepted
        return accepted
    }

    private fun updateCutoff(
        reading: Float,
        elapsedSeconds: Float,
    ): Float {
        val speed = when {
            elapsedSeconds > 0f -> {
                difference(from = previousInputDegrees, to = reading) / elapsedSeconds
            }
            else -> 0f
        }
        val speedWeight = smoothingWeight(cutoff = SPEED_CUTOFF, elapsedSeconds = elapsedSeconds)
        speedDegreesPerSecond += speedWeight * (speed - speedDegreesPerSecond)
        previousInputDegrees = reading
        return MINIMUM_CUTOFF + SPEED_WEIGHT * abs(speedDegreesPerSecond)
    }

    private fun updatePosition(
        current: Float,
        reading: Float,
        weight: Float,
    ): Float {
        val remainingDegrees = difference(from = current, to = reading)
        val smoothed = current + weight * remainingDegrees
        isSettled = abs(difference(from = smoothed, to = reading)) <= SETTLED_THRESHOLD_DEGREES
        val result = when {
            isSettled -> reading
            else -> smoothed
        }
        if (isSettled) {
            speedDegreesPerSecond = 0f
        }
        filteredDegrees = result
        return result
    }

    private fun difference(
        from: Float,
        to: Float,
    ): Float {
        return when {
            wrapsAtFullTurn -> shortestTurn(from = from, to = to)
            else -> to - from
        }
    }

    private fun smoothingWeight(
        cutoff: Float,
        elapsedSeconds: Float,
    ): Float {
        val timeConstant = 1f / (2f * PI.toFloat() * cutoff)
        return elapsedSeconds / (timeConstant + elapsedSeconds)
    }

    private companion object {
        private const val HALF_TURN_DEGREES = 180f
        private const val MOVEMENT_THRESHOLD_DEGREES = 0.35f
        private const val SETTLED_THRESHOLD_DEGREES = 0.01f
        private const val MINIMUM_CUTOFF = 2f
        private const val SPEED_CUTOFF = 1f
        private const val SPEED_WEIGHT = 0.2f
        private val MAXIMUM_FRAME_INTERVAL = 50.milliseconds
    }
}
