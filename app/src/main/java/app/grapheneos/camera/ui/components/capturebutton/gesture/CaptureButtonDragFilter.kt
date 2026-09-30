package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

internal class CaptureButtonDragFilter(
    private val touchSlop: Float,
) {

    private var total = Offset.Zero
    private var isHorizontalActive = false
    private var isVerticalActive = false

    fun filter(delta: Offset): Offset {
        total += delta
        isHorizontalActive = isHorizontalActive || abs(total.x) > touchSlop
        isVerticalActive = isVerticalActive || abs(total.y) > touchSlop

        return Offset(
            x = passIf(isActive = isHorizontalActive, value = delta.x),
            y = passIf(isActive = isVerticalActive, value = delta.y),
        )
    }

    private fun passIf(
        isActive: Boolean,
        value: Float,
    ): Float {
        return when {
            isActive -> value
            else -> 0f
        }
    }
}
