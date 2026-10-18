package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 3f
private const val DOUBLE_TAP_SCALE = 2.5f

@Stable
internal class ZoomGestureState {

    var scale by mutableFloatStateOf(MIN_SCALE)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    var isZoomAnimationEnabled by mutableStateOf(false)
        private set

    private var containerSize by mutableStateOf(Size.Zero)
    private var contentSize by mutableStateOf(Size.Zero)

    fun isZoomed(): Boolean {
        return scale > MIN_SCALE
    }

    fun updateLayout(
        containerSize: Size,
        contentSize: Size,
    ) {
        this.containerSize = containerSize
        this.contentSize = contentSize
        offset = coerceOffset(
            candidateOffset = offset,
            candidateScale = scale,
        )
    }

    fun applyTransform(
        centroid: Offset,
        zoomChange: Float,
        panChange: Offset,
    ) {
        if (!centroid.isSpecified) return

        isZoomAnimationEnabled = false
        zoomAround(
            focus = centroid,
            nextScale = (scale * zoomChange).coerceIn(
                minimumValue = MIN_SCALE,
                maximumValue = MAX_SCALE,
            ),
            panChange = panChange,
        )
    }

    fun applyDoubleTap(tapOffset: Offset) {
        isZoomAnimationEnabled = true
        zoomAround(
            focus = tapOffset,
            nextScale = when {
                isZoomed() -> MIN_SCALE
                else -> DOUBLE_TAP_SCALE
            },
        )
    }

    private fun zoomAround(
        focus: Offset,
        nextScale: Float,
        panChange: Offset = Offset.Zero,
    ) {
        val focusFromCenter = focus - containerSize.center
        val focusKeepingOffset = focusFromCenter - (focusFromCenter - offset) * (nextScale / scale)

        offset = coerceOffset(
            candidateOffset = focusKeepingOffset + panChange,
            candidateScale = nextScale,
        )
        scale = nextScale
    }

    private fun coerceOffset(
        candidateOffset: Offset,
        candidateScale: Float,
    ): Offset {
        return Offset(
            x = coerceWithinOverflow(
                value = candidateOffset.x,
                scaledContent = contentSize.width * candidateScale,
                container = containerSize.width,
            ),
            y = coerceWithinOverflow(
                value = candidateOffset.y,
                scaledContent = contentSize.height * candidateScale,
                container = containerSize.height,
            ),
        )
    }

    private fun coerceWithinOverflow(
        value: Float,
        scaledContent: Float,
        container: Float,
    ): Float {
        val overflow = (scaledContent - container) / 2f

        return when {
            overflow <= 0f -> 0f
            else -> value.coerceIn(
                minimumValue = -overflow,
                maximumValue = overflow,
            )
        }
    }
}
