package app.grapheneos.camera.ui.gallery.components.gesture

import android.os.Parcelable
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.parcelize.Parcelize

private const val FIT_SCALE = 1f
private const val MIN_SCALE = 0.3f
private const val MIN_ZOOM_LIMIT = 4f
private const val ZOOM_LIMIT_TO_ACTUAL_SIZE = 3f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val MIN_DRAG_SCALE = 0.85f
private const val CLOSE_PROGRESS = 0.5f

private val SHRINK_DISTANCE = 96.dp

@Composable
internal fun rememberViewerTransformState(pageKey: String?): ViewerTransformState {
    val shrinkDistance = with(LocalDensity.current) { SHRINK_DISTANCE.toPx() }

    return rememberSaveable(
        pageKey,
        shrinkDistance,
        saver = ViewerTransformState.saver(shrinkDistance),
    ) {
        ViewerTransformState(shrinkDistance = shrinkDistance)
    }
}

@Stable
internal class ViewerTransformState(
    private val shrinkDistance: Float,
    initialScale: Float = FIT_SCALE,
    initialOffset: Offset = Offset.Zero,
) {

    var scale by mutableFloatStateOf(initialScale)
        private set

    var offset by mutableStateOf(initialOffset)
        private set

    var rotation by mutableFloatStateOf(0f)
        private set

    var isTransforming by mutableStateOf(false)
        private set

    var isDismissed by mutableStateOf(false)
        private set

    var exitDirection by mutableFloatStateOf(1f)
        private set

    private var containerSize by mutableStateOf(Size.Zero)
    private var contentSize by mutableStateOf(Size.Zero)
    private var maxScale by mutableFloatStateOf(FIT_SCALE)
    private var smallestScale by mutableFloatStateOf(FIT_SCALE)

    private var canDismiss = false
    private var isDismissing = false
    private var isDragStarted = false
    private var travel = Offset.Zero

    fun isZoomed(): Boolean {
        return scale > FIT_SCALE
    }

    fun isZoomStoppedAtFit(): Boolean {
        return isTransforming && !canDismiss && scale == FIT_SCALE
    }

    fun isZoomStoppedAtLimit(): Boolean {
        return isTransforming && maxScale > FIT_SCALE && scale == maxScale
    }

    fun fadeProgress(): Float {
        return when {
            isDismissed -> 1f
            isTransforming -> progressAt(smallestScale)
            else -> 0f
        }
    }

    fun updateLayout(
        containerSize: Size,
        imageSize: Size?,
        canZoom: Boolean,
    ) {
        this.containerSize = containerSize
        contentSize = when (imageSize) {
            null -> containerSize
            else -> fittedSize(imageSize)
        }
        maxScale = when {
            canZoom && imageSize != null -> zoomLimit(imageSize)
            else -> FIT_SCALE
        }

        if (!isTransforming) {
            scale = scale.coerceAtMost(maxScale)
            offset = coerceOffset(
                candidateOffset = offset,
                candidateScale = scale,
            )
        }
    }

    fun startDrag() {
        start(isDrag = true)
    }

    fun startTransform() {
        start(isDrag = false)
    }

    fun applyTransform(
        centroid: Offset,
        panChange: Offset,
        zoomChange: Float = 1f,
        rotationChange: Float = 0f,
    ) {
        if (!centroid.isSpecified) return

        val requestedScale = scale * zoomChange * shrinkChange(panChange)
        canDismiss = canDismiss && (isDismissing || requestedScale <= FIT_SCALE)

        val minScale = when {
            canDismiss -> MIN_SCALE
            else -> FIT_SCALE
        }
        val nextScale = requestedScale.coerceIn(
            minimumValue = minScale,
            maximumValue = maxScale,
        )
        isDismissing = isDismissing || nextScale < FIT_SCALE

        val appliedRotation = when {
            isDismissing -> rotationChange
            else -> 0f
        }
        val focus = centroid - containerSize.center
        val focusToCenter = (offset - focus).rotateBy(appliedRotation) * (nextScale / scale)
        val nextOffset = focus + panChange + focusToCenter

        offset = when {
            isDismissing -> nextOffset
            else -> coerceOffset(
                candidateOffset = nextOffset,
                candidateScale = nextScale,
            )
        }
        scale = nextScale
        smallestScale = min(smallestScale, nextScale)
        rotation += appliedRotation

        if (isDragStarted && panChange.y != 0f) {
            exitDirection = sign(panChange.y)
        }
    }

    fun release() {
        isTransforming = false

        when {
            isDismissing && progressAt(scale) >= CLOSE_PROGRESS -> isDismissed = true
            else -> settle()
        }
    }

    fun cancel() {
        isTransforming = false
        settle()
    }

    fun applyDoubleTap(position: Offset) {
        val focus = offset + (position - containerSize.center) * scale
        val nextScale = when {
            isZoomed() -> FIT_SCALE
            else -> min(DOUBLE_TAP_SCALE, maxScale)
        }

        offset = coerceOffset(
            candidateOffset = focus - (focus - offset) * (nextScale / scale),
            candidateScale = nextScale,
        )
        scale = nextScale
    }

    private fun progressAt(scale: Float): Float {
        return ((FIT_SCALE - scale) / (FIT_SCALE - MIN_DRAG_SCALE)).coerceIn(
            minimumValue = 0f,
            maximumValue = 1f,
        )
    }

    private fun zoomLimit(imageSize: Size): Float {
        val actualSizeScale = imageSize.width / contentSize.width

        return max(MIN_ZOOM_LIMIT, ZOOM_LIMIT_TO_ACTUAL_SIZE * actualSizeScale)
    }

    private fun fittedSize(imageSize: Size): Size {
        val fitScale = min(
            containerSize.width / imageSize.width,
            containerSize.height / imageSize.height,
        )

        return imageSize * fitScale
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

    private fun start(isDrag: Boolean) {
        isTransforming = true
        isDragStarted = isDrag
        isDismissing = isDrag
        canDismiss = !isZoomed()
        smallestScale = scale
        travel = Offset.Zero
    }

    private fun shrinkChange(panChange: Offset): Float {
        if (!isDismissing) return 1f

        val previousShrink = dragShrink()
        travel += panChange

        return dragShrink() / previousShrink
    }

    private fun dragShrink(): Float {
        val progress = (travel.getDistance() / shrinkDistance).coerceAtMost(1f)

        return FIT_SCALE - (FIT_SCALE - MIN_DRAG_SCALE) * EaseOutQuad.transform(progress)
    }

    private fun Offset.rotateBy(degrees: Float): Offset {
        val radians = Math.toRadians(degrees.toDouble())
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()

        return Offset(
            x = x * cosine - y * sine,
            y = x * sine + y * cosine,
        )
    }

    private fun settle() {
        scale = scale.coerceIn(
            minimumValue = FIT_SCALE,
            maximumValue = maxScale,
        )
        offset = coerceOffset(
            candidateOffset = offset,
            candidateScale = scale,
        )
        rotation = 0f
        exitDirection = 1f
        canDismiss = false
        isDismissing = false
        isDragStarted = false
        travel = Offset.Zero
    }

    private fun toSavedState(): SavedState {
        return SavedState(
            scale = scale,
            offsetX = offset.x,
            offsetY = offset.y,
        )
    }

    @Parcelize
    internal data class SavedState(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
    ) : Parcelable

    companion object {
        fun saver(shrinkDistance: Float): Saver<ViewerTransformState, SavedState> {
            return Saver(
                save = { state -> state.toSavedState() },
                restore = { savedState ->
                    ViewerTransformState(
                        shrinkDistance = shrinkDistance,
                        initialScale = savedState.scale,
                        initialOffset = Offset(
                            x = savedState.offsetX,
                            y = savedState.offsetY,
                        ),
                    )
                },
            )
        }
    }
}
