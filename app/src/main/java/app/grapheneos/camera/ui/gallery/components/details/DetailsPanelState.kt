package app.grapheneos.camera.ui.gallery.components.details

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.grapheneos.camera.ui.core.animation.CONTROLS_SPEC
import app.grapheneos.camera.ui.core.animation.follow
import kotlin.math.abs
import kotlin.math.min

private const val PHOTO_WINDOW_HEIGHT_TO_WIDTH = 0.75f
private const val EXPANDED_TOP = 0f
private const val CLOSED_POSITION = 0f
private const val OPEN_POSITION = 1f
private const val EXPANDED_POSITION = 2f

private val SETTLE_VELOCITY = 125.dp
private val SIDE_PANEL_WIDTH = 360.dp

@Composable
internal fun rememberDetailsPanelState(isShown: Boolean): DetailsPanelState {
    val density = LocalDensity.current
    val cornerRadius = MaterialTheme.shapes.extraLarge.topStart.toPx(
        shapeSize = Size.Zero,
        density = density,
    )
    val settleVelocity = with(density) { SETTLE_VELOCITY.toPx() }
    val sidePanelWidth = with(density) { SIDE_PANEL_WIDTH.toPx() }

    return remember(cornerRadius, settleVelocity, sidePanelWidth) {
        DetailsPanelState(
            cornerRadius = cornerRadius,
            settleVelocity = settleVelocity,
            sidePanelWidth = sidePanelWidth,
            isShown = isShown,
        )
    }
}

@Composable
internal fun animateDetailsPositionAsState(
    detailsState: DetailsPanelState,
): State<Float> {
    val position = remember(detailsState) { Animatable(detailsState.targetPosition()) }

    LaunchedEffect(position) {
        position.follow(
            target = detailsState::targetPosition,
            isAnimated = { !detailsState.isDragging },
            animationSpec = CONTROLS_SPEC,
        )
    }

    return position.asState()
}

@Stable
internal class DetailsPanelState(
    private val cornerRadius: Float,
    private val settleVelocity: Float,
    private val sidePanelWidth: Float,
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

    fun isSidePanel(): Boolean {
        return containerSize.width > containerSize.height
    }

    fun panelWidth(): Float {
        return min(sidePanelWidth, containerSize.width / 2f)
    }

    fun isShown(): Boolean {
        return targetPosition() > CLOSED_POSITION
    }

    fun updateLayout(containerSize: Size) {
        this.containerSize = containerSize

        if (isSidePanel() && anchor == DetailsAnchor.Expanded) {
            anchor = DetailsAnchor.Open
        }
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

    fun panelOffsetAt(position: Float): Offset {
        return when {
            isSidePanel() -> Offset(
                x = panelWidth() * (1f - openingAt(position)),
                y = 0f,
            )
            else -> Offset(
                x = 0f,
                y = topAt(position),
            )
        }
    }

    fun photoShiftAt(position: Float): Offset {
        val opening = openingAt(position)

        return when {
            isSidePanel() -> Offset(
                x = lerp(
                    start = 0f,
                    stop = -panelWidth() / 2f,
                    fraction = opening,
                ),
                y = 0f,
            )
            else -> Offset(
                x = 0f,
                y = lerp(
                    start = 0f,
                    stop = (photoWindowHeight() - containerSize.height) / 2f,
                    fraction = opening,
                ),
            )
        }
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

        return !isSidePanel() && (isOverflowing || anchor == DetailsAnchor.Expanded)
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
        return when {
            isSidePanel() -> closedTop() - panelWidth()
            else -> photoWindowHeight() - cornerRadius
        }
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
