package app.grapheneos.camera.ui.components.ruler

import androidx.annotation.CallSuper
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

@Stable
internal abstract class RulerState(
    initialPosition: Float,
    private val currentScale: State<RulerScale>,
    private val currentTickSpacing: State<Float>,
    private val currentOnValueChangeFinished: State<() -> Unit>,
) : DraggableState,
    DragScope {

    var position by mutableFloatStateOf(initialPosition)
        private set

    var isDragging by mutableStateOf(false)
        private set

    private val mutex = MutatorMutex()

    override suspend fun drag(
        dragPriority: MutatePriority,
        block: suspend DragScope.() -> Unit,
    ) {
        mutex.mutateWith(
            receiver = this,
            priority = dragPriority,
        ) {
            isDragging = true
            block()
        }
    }

    override fun dispatchRawDelta(delta: Float) {
        dragBy(pixels = delta)
    }

    final override fun dragBy(pixels: Float) {
        val previousPosition = position

        position = (position - pixels / currentTickSpacing.value)
            .coerceIn(0f, currentScale.value.lastTick.toFloat())
        onDragged(
            from = previousPosition,
            to = position,
        )
    }

    @CallSuper
    open suspend fun release() {
        isDragging = false
        currentOnValueChangeFinished.value()
    }

    abstract fun isReported(value: Float): Boolean

    abstract suspend fun syncTo(value: Float)

    protected abstract fun onDragged(
        from: Float,
        to: Float,
    )

    protected suspend fun animatePosition(
        target: Float,
        onStart: () -> Unit,
    ) {
        mutex.mutate {
            onStart()
            animate(
                initialValue = position,
                targetValue = target,
                animationSpec = SETTLE_SPEC,
            ) { value, _ ->
                position = value
            }
        }
    }

    private companion object {
        private val SETTLE_SPEC = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 500f,
        )
    }
}

@Composable
internal fun RulerSyncEffect(
    state: RulerState,
    value: Float,
) {
    val syncTarget = when {
        state.isDragging -> null
        state.isReported(value = value) -> null
        else -> value
    }
    val currentSyncTarget = rememberUpdatedState(syncTarget)

    LaunchedEffect(state) {
        snapshotFlow { currentSyncTarget.value }
            .filterNotNull()
            .collectLatest { target -> state.syncTo(value = target) }
    }
}
