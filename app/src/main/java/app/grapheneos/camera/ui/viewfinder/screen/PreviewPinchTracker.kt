package app.grapheneos.camera.ui.viewfinder.screen

import kotlin.math.abs

// TODO: replace with Modifier.transformable on the preview once its gestures move to Compose.
internal class PreviewPinchTracker(
    private val spanSlop: Float,
) {

    var isPinching = false
        private set

    private var isTracking = false
    private var initialSpan = 0f
    private var previousSpan = 0f

    fun restart(span: Float) {
        isTracking = true
        initialSpan = span
        previousSpan = span
    }

    fun move(span: Float): Pinch? {
        val pinch = when {
            !isTracking || span <= 0f -> null
            isPinching && previousSpan > 0f -> Pinch.Scaled(scaleFactor = span / previousSpan)
            !isPinching && abs(span - initialSpan) > spanSlop -> Pinch.Started
            else -> null
        }
        if (pinch != null) {
            isPinching = true
            previousSpan = span
        }

        return pinch
    }

    fun reject() {
        isTracking = false
        isPinching = false
    }

    fun stop(): Boolean {
        val wasPinching = isPinching
        isTracking = false
        isPinching = false

        return wasPinching
    }

    sealed interface Pinch {

        data object Started : Pinch

        data class Scaled(
            val scaleFactor: Float,
        ) : Pinch
    }
}
