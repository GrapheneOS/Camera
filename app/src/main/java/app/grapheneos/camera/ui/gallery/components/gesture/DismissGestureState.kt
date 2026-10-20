package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.sign

private const val MIN_SCALE = 0.3f
private const val MIN_DRAG_SCALE = 0.85f
private const val CLOSE_PROGRESS = 0.5f

private val SHRINK_DISTANCE = 96.dp

@Composable
internal fun rememberDismissGestureState(): DismissGestureState {
    val shrinkDistance = with(LocalDensity.current) { SHRINK_DISTANCE.toPx() }

    return remember(shrinkDistance) {
        DismissGestureState(shrinkDistance = shrinkDistance)
    }
}

@Stable
internal class DismissGestureState(
    private val shrinkDistance: Float,
) {

    var origin by mutableStateOf(Offset.Zero)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    var scale by mutableFloatStateOf(1f)
        private set

    var rotation by mutableFloatStateOf(0f)
        private set

    var isDragging by mutableStateOf(false)
        private set

    var isDismissed by mutableStateOf(false)
        private set

    var exitDirection by mutableFloatStateOf(1f)
        private set

    private var pinchScale = 1f

    fun start(origin: Offset) {
        this.origin = origin
        isDragging = true
    }

    fun applyTransform(
        panChange: Offset,
        zoomChange: Float = 1f,
        rotationChange: Float = 0f,
    ) {
        offset += panChange
        rotation += rotationChange

        if (panChange.y != 0f) {
            exitDirection = sign(panChange.y)
        }

        val dragScale = dragScale()
        scale = (dragScale * pinchScale * zoomChange).coerceIn(
            minimumValue = MIN_SCALE,
            maximumValue = 1f,
        )
        pinchScale = scale / dragScale
    }

    fun release() {
        isDragging = false

        when {
            progressAt(scale) >= CLOSE_PROGRESS -> isDismissed = true
            else -> reset()
        }
    }

    fun cancel() {
        isDragging = false
        reset()
    }

    fun progressAt(scale: Float): Float {
        return ((1f - scale) / (1f - MIN_DRAG_SCALE)).coerceIn(
            minimumValue = 0f,
            maximumValue = 1f,
        )
    }

    private fun reset() {
        offset = Offset.Zero
        scale = 1f
        rotation = 0f
        pinchScale = 1f
        exitDirection = 1f
    }

    private fun dragScale(): Float {
        val travel = (offset.getDistance() / shrinkDistance).coerceAtMost(1f)

        return 1f - (1f - MIN_DRAG_SCALE) * EaseOutQuad.transform(travel)
    }
}
