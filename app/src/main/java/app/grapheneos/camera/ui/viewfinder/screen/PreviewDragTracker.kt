package app.grapheneos.camera.ui.viewfinder.screen

import kotlin.math.abs
import kotlin.math.sign

// TODO: replace with Modifier.draggable on the preview once its gestures move to Compose.
internal class PreviewDragTracker(
    private val touchSlop: Float,
) {

    var isDraggingVertically = false
        private set

    private var isDeciding = false
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f

    fun down(
        x: Float,
        y: Float,
        canDragVertically: Boolean,
    ) {
        isDeciding = canDragVertically
        isDraggingVertically = false
        downX = x
        downY = y
        lastY = y
    }

    fun move(
        x: Float,
        y: Float,
    ): VerticalDrag? {
        val drag = when {
            isDraggingVertically -> VerticalDrag.Moved(delta = y - lastY)
            isDeciding -> decide(x = x, y = y)
            else -> null
        }
        lastY = y

        return drag
    }

    fun stop(): Boolean {
        val wasDragging = isDraggingVertically
        isDeciding = false
        isDraggingVertically = false

        return wasDragging
    }

    private fun decide(
        x: Float,
        y: Float,
    ): VerticalDrag? {
        val distanceX = x - downX
        val distanceY = y - downY
        if (distanceX * distanceX + distanceY * distanceY <= touchSlop * touchSlop) return null

        isDeciding = false
        isDraggingVertically = abs(distanceY) > abs(distanceX)

        return when {
            isDraggingVertically -> VerticalDrag.Started(
                delta = distanceY - sign(distanceY) * touchSlop,
            )
            else -> null
        }
    }

    sealed interface VerticalDrag {

        val delta: Float

        data class Started(
            override val delta: Float,
        ) : VerticalDrag

        data class Moved(
            override val delta: Float,
        ) : VerticalDrag
    }
}
