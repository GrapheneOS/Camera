package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 3f
private const val DOUBLE_TAP_SCALE = 1.75f

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
        zoomChange: Float,
        panChange: Offset,
    ): Boolean {
        isZoomAnimationEnabled = false
        val nextScale = (scale * zoomChange).coerceIn(
            minimumValue = MIN_SCALE,
            maximumValue = MAX_SCALE,
        )

        scale = nextScale
        offset = coerceOffset(
            candidateOffset = offset + panChange,
            candidateScale = nextScale,
        )

        return zoomChange != 1f
    }

    fun applyDoubleTap(tapOffset: Offset) {
        isZoomAnimationEnabled = true
        val nextScale = when {
            isZoomed() -> MIN_SCALE
            else -> DOUBLE_TAP_SCALE
        }

        scale = nextScale
        offset = doubleTapOffset(
            tapOffset = tapOffset,
            nextScale = nextScale,
        )
    }

    fun reset() {
        isZoomAnimationEnabled = false
        scale = MIN_SCALE
        offset = Offset.Zero
    }

    private fun coerceOffset(
        candidateOffset: Offset,
        candidateScale: Float,
    ): Offset {
        val maxX = overflow(contentSize.width * candidateScale, containerSize.width)
        val maxY = overflow(contentSize.height * candidateScale, containerSize.height)

        return Offset(
            x = candidateOffset.x.coerceIn(
                minimumValue = -maxX,
                maximumValue = maxX,
            ),
            y = candidateOffset.y.coerceIn(
                minimumValue = -maxY,
                maximumValue = maxY,
            ),
        )
    }

    private fun overflow(
        scaledContent: Float,
        container: Float,
    ): Float {
        return ((scaledContent - container) / 2f).coerceAtLeast(0f)
    }

    private fun doubleTapOffset(
        tapOffset: Offset,
        nextScale: Float,
    ): Offset {
        val tapOffsetFromCenter = tapOffset - containerSize.center

        return coerceOffset(
            candidateOffset = -tapOffsetFromCenter * (nextScale - MIN_SCALE),
            candidateScale = nextScale,
        )
    }
}
