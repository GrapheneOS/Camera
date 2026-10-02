package app.grapheneos.camera.ui.components.adjustmentbar.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.grapheneos.camera.ui.components.adjustmentbar.AdjustmentBarScale
import app.grapheneos.camera.ui.components.ruler.RulerState
import app.grapheneos.camera.ui.components.ruler.RulerSyncEffect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Stable
internal class AdjustmentBarState(
    initialTick: Int,
    private val currentScale: State<AdjustmentBarScale>,
    currentTickSpacing: State<Float>,
    private val currentHapticFeedback: State<HapticFeedback>,
    private val currentValue: State<Float>,
    private val currentOnValueChange: State<(Float) -> Unit>,
    private val currentOnValueChangeFinished: State<() -> Unit>,
) : RulerState(
    initialPosition = initialTick.toFloat(),
    currentScale = currentScale,
    currentTickSpacing = currentTickSpacing,
    currentOnValueChangeFinished = currentOnValueChangeFinished,
) {

    private var reportedTick by mutableIntStateOf(initialTick)

    override fun isReported(value: Float): Boolean {
        return currentScale.value.tickOf(value = value) == reportedTick
    }

    override suspend fun release() {
        val target = currentScale.value.nearestTick(position = position)
        val isNewTick = target != reportedTick

        if (isNewTick) {
            report(tick = target)
        }
        super.release()
        animateTo(tick = target)
        if (isNewTick) {
            performTickFeedback(tick = target)
        }
    }

    override suspend fun syncTo(value: Float) {
        animateTo(tick = currentScale.value.tickOf(value = value))
    }

    override fun onDragged(
        from: Float,
        to: Float,
    ) {
        val passedTick = passedTick(
            from = from,
            to = to,
        )
        if (passedTick != reportedTick) {
            report(tick = passedTick)
            performTickFeedback(tick = passedTick)
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

    private suspend fun animateTo(tick: Int) {
        animatePosition(
            target = tick.toFloat(),
            onStart = { reportedTick = tick },
        )
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

    RulerSyncEffect(
        state = state,
        value = value,
    )

    return state
}
