package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget
import kotlin.math.abs

internal class CaptureButtonTargetTracker(
    private val targets: List<CaptureButtonTarget>,
    private val density: Density,
    private val layoutDirection: LayoutDirection,
) {

    var armedTarget: CaptureButtonTarget? = null
        private set

    fun update(offset: Offset): CaptureButtonTarget? {
        val armed = armedTarget

        armedTarget = when {
            armed != null && progress(armed, offset) >= DISARM_PROGRESS -> armed
            else -> targets.fastFirstOrNull { progress(it, offset) >= 1f }
        }

        return armedTarget
    }

    fun leadsToTarget(
        offset: Offset,
        touchSlop: Float,
    ): Boolean {
        return targets.fastAny { target ->
            val unitVector = target.direction.unitVector(layoutDirection = layoutDirection)
            val travelled = offset.x * unitVector.x + offset.y * unitVector.y
            val across = abs(offset.x * unitVector.y - offset.y * unitVector.x)

            travelled > touchSlop && travelled > across
        }
    }

    private fun progress(
        target: CaptureButtonTarget,
        offset: Offset,
    ): Float {
        return target.progress(
            offset = offset,
            density = density,
            layoutDirection = layoutDirection,
        )
    }

    private companion object {
        private const val DISARM_PROGRESS = 0.8f
    }
}
