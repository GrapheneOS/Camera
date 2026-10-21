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

private const val PHOTO_WINDOW_HEIGHT_TO_WIDTH = 0.75f
private const val OPEN_PROGRESS = 1f
private const val CLOSED_PROGRESS = 0f
private const val HALF_PROGRESS = 0.5f

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

    var progress by mutableFloatStateOf(progressFor(isShown))
        private set

    var isDragging by mutableStateOf(false)
        private set

    private var containerSize by mutableStateOf(Size.Zero)

    fun isShown(): Boolean {
        return progress > CLOSED_PROGRESS
    }

    fun updateLayout(containerSize: Size) {
        this.containerSize = containerSize
    }

    fun show(isShown: Boolean) {
        if (!isDragging) {
            progress = progressFor(isShown)
        }
    }

    fun startDrag() {
        isDragging = true
    }

    fun drag(panChange: Float) {
        progress = (progress - panChange / travel()).coerceIn(
            minimumValue = CLOSED_PROGRESS,
            maximumValue = OPEN_PROGRESS,
        )
    }

    fun release(velocity: Float): Boolean {
        val opens = when {
            velocity <= -settleVelocity -> true
            velocity >= settleVelocity -> false
            else -> progress >= HALF_PROGRESS
        }

        isDragging = false
        progress = progressFor(opens)

        return opens
    }

    fun panelHeight(): Float {
        return containerSize.height - openPanelTop()
    }

    fun panelShift(progress: Float): Float {
        return lerp(
            start = containerSize.height,
            stop = openPanelTop(),
            fraction = progress,
        ) - openPanelTop()
    }

    fun photoShift(progress: Float): Float {
        val windowCenter = photoWindowHeight() / 2f

        return lerp(
            start = 0f,
            stop = windowCenter - containerSize.height / 2f,
            fraction = progress,
        )
    }

    private fun travel(): Float {
        return (containerSize.height - openPanelTop()).coerceAtLeast(1f)
    }

    private fun openPanelTop(): Float {
        return photoWindowHeight() - cornerRadius
    }

    private fun photoWindowHeight(): Float {
        return containerSize.width * PHOTO_WINDOW_HEIGHT_TO_WIDTH
    }

    private fun progressFor(isShown: Boolean): Float {
        return when {
            isShown -> OPEN_PROGRESS
            else -> CLOSED_PROGRESS
        }
    }
}
