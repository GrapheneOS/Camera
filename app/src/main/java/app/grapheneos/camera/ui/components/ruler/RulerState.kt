package app.grapheneos.camera.ui.components.ruler

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.grapheneos.camera.ui.components.gesture.DragEndInteractions
import app.grapheneos.camera.ui.components.motion.SETTLE_SPEC
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

@Stable
internal abstract class RulerState(
    initialPosition: Float,
    private val currentScale: State<RulerScale>,
    protected val bindings: RulerBindings,
) : DraggableState,
    DragScope {

    var position by mutableFloatStateOf(initialPosition)
        private set

    var isDragging by mutableStateOf(false)
        private set

    internal var revision by mutableIntStateOf(0)
        private set

    private var forwardingInteractions: DragEndInteractions? = null
    private var isDragCanceled = false

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

        position = dragTarget(
            from = previousPosition,
            ticks = -pixels / bindings.tickSpacing.value,
        ).coerceIn(0f, currentScale.value.lastTick.toFloat())

        onDragged(
            from = previousPosition,
            to = position,
        )
    }

    open suspend fun release() {
        finishDrag()
    }

    open suspend fun cancel() {
        release()
    }

    abstract fun step(ticks: Int): Boolean

    abstract fun moveTo(value: Float): Boolean

    abstract fun isReported(value: Float): Boolean

    abstract suspend fun syncTo(value: Float)

    internal suspend fun endDrag() {
        when {
            isDragCanceled -> cancel()
            else -> release()
        }
    }

    internal fun dragInteractions(forwardTo: MutableInteractionSource): MutableInteractionSource {
        val current = forwardingInteractions

        return when {
            current != null && current.forwardTo === forwardTo -> current
            else -> DragEndInteractions(
                onStop = { isDragCanceled = false },
                onCancel = { isDragCanceled = true },
                forwardTo = forwardTo,
            ).also { forwardingInteractions = it }
        }
    }

    protected open fun dragTarget(
        from: Float,
        ticks: Float,
    ): Float {
        return from + ticks
    }

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

    protected fun finishDrag() {
        isDragging = false
        revision++
        bindings.onValueChangeFinished.value()
    }

    protected fun commit(value: Float) {
        bindings.onValueChange.value(value)
        bindings.onValueChangeFinished.value()
    }

    protected fun report(value: Float) {
        bindings.onValueChange.value(value)
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

    LaunchedEffect(state, state.revision) {
        snapshotFlow { currentSyncTarget.value }
            .filterNotNull()
            .collectLatest { target -> state.syncTo(value = target) }
    }
}
