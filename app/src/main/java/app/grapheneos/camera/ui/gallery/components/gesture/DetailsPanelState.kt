package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs

private const val PHOTO_WINDOW_HEIGHT_TO_WIDTH = 0.75f
private const val EXPANDED_TOP = 0f
private const val CLOSED_POSITION = 0f
private const val OPEN_POSITION = 1f
private const val EXPANDED_POSITION = 2f

private val SETTLE_VELOCITY = 125.dp

@Composable
internal fun rememberDetailsPanelState(isShown: Boolean): DetailsPanelState {
    val density = LocalDensity.current
    val cornerRadius = MaterialTheme.shapes.extraLarge.topStart.toPx(
        shapeSize = Size.Zero,
        density = density,
    )
    val settleVelocity = with(density) { SETTLE_VELOCITY.toPx() }

    return remember(cornerRadius, settleVelocity) {
        DetailsPanelState(
            cornerRadius = cornerRadius,
            settleVelocity = settleVelocity,
            isShown = isShown,
        )
    }
}

@Stable
internal class DetailsPanelState(
    private val cornerRadius: Float,
    private val settleVelocity: Float,
    isShown: Boolean = false,
) {

    var anchor by mutableStateOf(anchorFor(isShown))
        private set

    var isDragging by mutableStateOf(false)
        private set

    private var dragTop by mutableFloatStateOf(0f)
    private var containerSize by mutableStateOf(Size.Zero)
    private var contentHeight = 0f

    fun isLaidOut(): Boolean {
        return containerSize != Size.Zero
    }

    fun isShown(): Boolean {
        return targetPosition() > CLOSED_POSITION
    }

    fun updateLayout(containerSize: Size) {
        this.containerSize = containerSize
    }

    fun updateContentHeight(contentHeight: Float) {
        this.contentHeight = contentHeight
    }

    fun show(isShown: Boolean) {
        if (isDragging) return

        anchor = when {
            !isShown -> DetailsAnchor.Closed
            anchor == DetailsAnchor.Closed -> DetailsAnchor.Open
            else -> anchor
        }
    }

    fun targetPosition(): Float {
        return when {
            isDragging -> positionOf(dragTop)
            else -> positionOf(anchor)
        }
    }

    fun startDrag() {
        dragTop = topAt(targetPosition())
        isDragging = true
    }

    fun drag(panChange: Float): Float {
        val previousTop = dragTop
        val highestTop = when {
            canExpand() -> EXPANDED_TOP
            else -> openTop()
        }

        dragTop = (dragTop + panChange).coerceIn(
            minimumValue = highestTop,
            maximumValue = closedTop(),
        )

        return dragTop - previousTop
    }

    fun cancelDrag() {
        isDragging = false
    }

    fun release(velocity: Float): Boolean {
        anchor = when {
            velocity <= -settleVelocity -> nextAnchorUp()
            velocity >= settleVelocity -> nextAnchorDown()
            else -> nearestAnchor()
        }
        isDragging = false

        return anchor != DetailsAnchor.Closed
    }

    fun topAt(position: Float): Float {
        return when {
            position <= OPEN_POSITION -> lerp(
                start = closedTop(),
                stop = openTop(),
                fraction = position,
            )
            else -> lerp(
                start = openTop(),
                stop = EXPANDED_TOP,
                fraction = position - OPEN_POSITION,
            )
        }
    }

    fun photoShiftAt(position: Float): Float {
        val windowCenter = photoWindowHeight() / 2f

        return lerp(
            start = 0f,
            stop = windowCenter - containerSize.height / 2f,
            fraction = openingAt(position),
        )
    }

    fun openingAt(position: Float): Float {
        return position.coerceIn(
            minimumValue = CLOSED_POSITION,
            maximumValue = OPEN_POSITION,
        )
    }

    fun backButtonVisibilityAt(position: Float): Float {
        return openingAt(position) * (1f - expansionAt(position))
    }

    fun expansionAt(position: Float): Float {
        return (position - OPEN_POSITION).coerceIn(
            minimumValue = CLOSED_POSITION,
            maximumValue = OPEN_POSITION,
        )
    }

    private fun canExpand(): Boolean {
        val isOverflowing = contentHeight > containerSize.height - openTop()

        return isOverflowing || anchor == DetailsAnchor.Expanded
    }

    private fun anchors(): List<DetailsAnchor> {
        return DetailsAnchor.entries.filter { it != DetailsAnchor.Expanded || canExpand() }
    }

    private fun nextAnchorUp(): DetailsAnchor {
        return anchors()
            .filter { topOf(it) < dragTop }
            .maxByOrNull(::topOf)
            ?: anchors().minBy(::topOf)
    }

    private fun nextAnchorDown(): DetailsAnchor {
        return anchors()
            .filter { topOf(it) > dragTop }
            .minByOrNull(::topOf)
            ?: DetailsAnchor.Closed
    }

    private fun nearestAnchor(): DetailsAnchor {
        return anchors().minBy { abs(topOf(it) - dragTop) }
    }

    private fun positionOf(anchor: DetailsAnchor): Float {
        return when (anchor) {
            DetailsAnchor.Closed -> CLOSED_POSITION
            DetailsAnchor.Open -> OPEN_POSITION
            DetailsAnchor.Expanded -> EXPANDED_POSITION
        }
    }

    private fun positionOf(top: Float): Float {
        return when {
            top >= openTop() -> openingAtTop(top)
            else -> OPEN_POSITION + (openTop() - top) / openTop().coerceAtLeast(1f)
        }
    }

    private fun openingAtTop(top: Float): Float {
        val travel = (closedTop() - openTop()).coerceAtLeast(1f)

        return ((closedTop() - top) / travel).coerceIn(
            minimumValue = CLOSED_POSITION,
            maximumValue = OPEN_POSITION,
        )
    }

    private fun topOf(anchor: DetailsAnchor): Float {
        return when (anchor) {
            DetailsAnchor.Closed -> closedTop()
            DetailsAnchor.Open -> openTop()
            DetailsAnchor.Expanded -> EXPANDED_TOP
        }
    }

    private fun closedTop(): Float {
        return containerSize.height
    }

    private fun openTop(): Float {
        return photoWindowHeight() - cornerRadius
    }

    private fun photoWindowHeight(): Float {
        return containerSize.width * PHOTO_WINDOW_HEIGHT_TO_WIDTH
    }

    private fun anchorFor(isShown: Boolean): DetailsAnchor {
        return when {
            isShown -> DetailsAnchor.Open
            else -> DetailsAnchor.Closed
        }
    }
}

internal enum class DetailsAnchor {
    Closed,
    Open,
    Expanded,
}
