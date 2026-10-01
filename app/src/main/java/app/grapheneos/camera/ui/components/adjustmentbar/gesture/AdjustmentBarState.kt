package app.grapheneos.camera.ui.components.adjustmentbar.gesture

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.grapheneos.camera.ui.components.adjustmentbar.AdjustmentBarScale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private val SETTLE_SPEC = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 500f,
)

/** [position] is in fractional ticks; a tick is reached when it passes the indicator. */
@Stable
internal class AdjustmentBarState(
    initialTick: Int,
    private val currentScale: State<AdjustmentBarScale>,
    private val currentTickSpacing: State<Float>,
    private val currentHapticFeedback: State<HapticFeedback>,
    private val currentValue: State<Float>,
    private val currentOnValueChange: State<(Float) -> Unit>,
    private val currentOnValueChangeFinished: State<() -> Unit>,
) : DraggableState,
    DragScope {

    var position by mutableFloatStateOf(initialTick.toFloat())
        private set

    var reportedTick = initialTick
        private set

    private val mutex = MutatorMutex()

    override suspend fun drag(
        dragPriority: MutatePriority,
        block: suspend DragScope.() -> Unit,
    ) {
        mutex.mutateWith(
            receiver = this,
            priority = dragPriority,
            block = block,
        )
    }

    override fun dispatchRawDelta(delta: Float) {
        dragBy(pixels = delta)
    }

    override fun dragBy(pixels: Float) {
        val previousPosition = position

        position = (position - pixels / currentTickSpacing.value)
            .coerceIn(0f, currentScale.value.lastTick.toFloat())

        val passedTick = passedTick(
            from = previousPosition,
            to = position,
        )
        if (passedTick != reportedTick) {
            report(tick = passedTick)
            performTickFeedback(tick = passedTick)
        }
    }

    suspend fun release() {
        val target = currentScale.value.nearestTick(position = position)
        val isNewTick = target != reportedTick

        if (isNewTick) {
            report(tick = target)
        }
        currentOnValueChangeFinished.value()
        animateTo(tick = target)
        if (isNewTick) {
            performTickFeedback(tick = target)
        }
    }

    suspend fun animateTo(tick: Int) {
        mutex.mutate {
            reportedTick = tick
            animate(
                initialValue = position,
                targetValue = tick.toFloat(),
                animationSpec = SETTLE_SPEC,
            ) { value, _ ->
                position = value
            }
        }
    }

    fun step(ticks: Int): Boolean {
        return moveTo(tick = valueTick() + ticks)
    }

    fun stepWithFeedback(ticks: Int): Boolean {
        val target = (valueTick() + ticks).coerceIn(0, currentScale.value.lastTick)
        val moves = moveTo(tick = target)

        if (moves) {
            performTickFeedback(tick = target)
        }

        return moves
    }

    fun moveTo(tick: Int): Boolean {
        val scale = currentScale.value
        val target = tick.coerceIn(0, scale.lastTick)
        val moves = target != valueTick()

        if (moves) {
            currentOnValueChange.value(scale.value(tick = target))
            currentOnValueChangeFinished.value()
        }

        return moves
    }

    private fun valueTick(): Int {
        return currentScale.value.tickOf(value = currentValue.value)
    }

    private fun performTickFeedback(tick: Int) {
        val type = when (tick) {
            0, currentScale.value.lastTick -> HapticFeedbackType.SegmentTick
            else -> HapticFeedbackType.SegmentFrequentTick
        }

        currentHapticFeedback.value.performHapticFeedback(type)
    }

    private fun report(tick: Int) {
        reportedTick = tick
        currentOnValueChange.value(currentScale.value.value(tick = tick))
    }

    private fun passedTick(
        from: Float,
        to: Float,
    ): Int {
        val candidate = when {
            to > from -> floor(to)
            else -> ceil(to)
        }
        val isPassed = candidate >= min(from, to) && candidate <= max(from, to)

        return when {
            isPassed && candidate != from -> candidate.toInt()
            else -> reportedTick
        }
    }
}

@Composable
internal fun rememberAdjustmentBarState(
    value: Float,
    scale: AdjustmentBarScale,
    tickSpacing: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
): AdjustmentBarState {
    val currentScale = rememberUpdatedState(scale)
    val currentTickSpacing = rememberUpdatedState(tickSpacing)
    val currentHapticFeedback = rememberUpdatedState(LocalHapticFeedback.current)
    val currentValue = rememberUpdatedState(value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished = rememberUpdatedState(onValueChangeFinished)
    val state = remember {
        AdjustmentBarState(
            initialTick = scale.tickOf(value = value),
            currentScale = currentScale,
            currentTickSpacing = currentTickSpacing,
            currentHapticFeedback = currentHapticFeedback,
            currentValue = currentValue,
            currentOnValueChange = currentOnValueChange,
            currentOnValueChangeFinished = currentOnValueChangeFinished,
        )
    }

    LaunchedEffect(state, scale, value) {
        val tick = scale.tickOf(value = value)

        if (tick != state.reportedTick) {
            state.animateTo(tick = tick)
        }
    }

    return state
}
