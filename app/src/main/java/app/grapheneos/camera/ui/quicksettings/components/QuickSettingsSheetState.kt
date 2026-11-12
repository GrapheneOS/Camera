package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.grapheneos.camera.ui.core.animation.SETTLE_SPEC
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

private const val HIDDEN_POSITION = 0f
private const val OPEN_POSITION = 1f
private const val HALFWAY_POSITION = 0.5f

@Stable
internal class QuickSettingsSheetState(
    private val settleVelocity: Float,
    isShown: Boolean = false,
) {

    var isDragging by mutableStateOf(false)
        private set

    val isHidden by derivedStateOf { !isDragging && position.value == HIDDEN_POSITION }

    var isShown by mutableStateOf(isShown)
        private set

    private var height = 0f
    private var releaseVelocity = 0f
    private var dragPosition by mutableFloatStateOf(HIDDEN_POSITION)
    private val position = Animatable(initialValue = positionOf(isShown)).apply {
        updateBounds(
            lowerBound = HIDDEN_POSITION,
            upperBound = OPEN_POSITION,
        )
    }

    fun show(isShown: Boolean) {
        if (isDragging) return

        if (this.isShown != isShown) {
            releaseVelocity = 0f
        }
        this.isShown = isShown
    }

    fun startDrag() {
        dragPosition = position.value
        releaseVelocity = 0f
        isDragging = true
    }

    fun drag(delta: Float): Float {
        if (height == 0f) return 0f

        val previousPosition = dragPosition
        dragPosition = (dragPosition - delta / height).coerceIn(
            minimumValue = HIDDEN_POSITION,
            maximumValue = OPEN_POSITION,
        )

        return (previousPosition - dragPosition) * height
    }

    fun release(velocity: Float): Boolean {
        isShown = when {
            velocity <= -settleVelocity -> true
            velocity >= settleVelocity -> false
            else -> dragPosition >= HALFWAY_POSITION
        }
        releaseVelocity = when (height) {
            0f -> 0f
            else -> -velocity / height
        }
        isDragging = false

        return isShown
    }

    fun updateHeight(height: Float) {
        this.height = height
    }

    fun offset(): Float {
        return (OPEN_POSITION - visibleFraction()) * height
    }

    fun visibleFraction(): Float {
        return position.value
    }

    suspend fun awaitShown() {
        snapshotFlow { visibleFraction() }
            .first { fraction -> fraction == OPEN_POSITION }
    }

    suspend fun animate() {
        snapshotFlow(
            ::targetPosition,
        ).collectLatest { target ->
            when {
                isDragging -> position.snapTo(target)
                else -> position.animateTo(
                    targetValue = target,
                    animationSpec = SETTLE_SPEC,
                    initialVelocity = releaseVelocity,
                )
            }
            releaseVelocity = 0f
        }
    }

    fun targetPosition(): Float {
        return when {
            isDragging -> dragPosition
            else -> positionOf(isShown)
        }
    }

    private fun positionOf(isShown: Boolean): Float {
        return when {
            isShown -> OPEN_POSITION
            else -> HIDDEN_POSITION
        }
    }
}
