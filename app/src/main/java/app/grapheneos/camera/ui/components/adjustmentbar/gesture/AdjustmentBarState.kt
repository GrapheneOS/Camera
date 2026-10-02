package app.grapheneos.camera.ui.components.adjustmentbar.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.grapheneos.camera.ui.components.adjustmentbar.AdjustmentBarScale
import app.grapheneos.camera.ui.components.ruler.RulerBindings
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
    bindings: RulerBindings,
) : RulerState(
    initialPosition = initialTick.toFloat(),
    currentScale = currentScale,
    bindings = bindings,
) {

    private var reportedTick by mutableIntStateOf(initialTick)

    override fun step(ticks: Int): Boolean {
        return moveToTick(tick = valueTick() + ticks)
    }

    override fun moveTo(value: Float): Boolean {
        return moveToTick(tick = currentScale.value.tickOf(value = value))
    }

    override fun isReported(value: Float): Boolean {
        return currentScale.value.tickOf(value = value) == reportedTick
    }

    override suspend fun release() {
        val target = currentScale.value.nearestTick(position = position)
        val isNewTick = target != reportedTick

        if (isNewTick) {
            reportTick(tick = target)
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
            reportTick(tick = passedTick)
            performTickFeedback(tick = passedTick)
        }
    }

    fun stepWithFeedback(ticks: Int): Boolean {
        val target = (valueTick() + ticks).coerceIn(0, currentScale.value.lastTick)
        val moves = moveToTick(tick = target)

        if (moves) {
            performTickFeedback(tick = target)
        }

        return moves
    }

    private fun moveToTick(tick: Int): Boolean {
        val target = tick.coerceIn(0, currentScale.value.lastTick)
        val moves = target != valueTick()

        if (moves) {
            commit(value = currentScale.value.value(position = target.toFloat()))
        }

        return moves
    }

    private fun valueTick(): Int {
        return currentScale.value.tickOf(value = bindings.value.value)
    }

    private suspend fun animateTo(tick: Int) {
        animatePosition(
            target = tick.toFloat(),
            onStart = { reportedTick = tick },
        )
    }

    private fun reportTick(tick: Int) {
        reportedTick = tick
        report(value = currentScale.value.value(position = tick.toFloat()))
    }

    private fun performTickFeedback(tick: Int) {
        val type = when (tick) {
            0, currentScale.value.lastTick -> HapticFeedbackType.SegmentTick
            else -> HapticFeedbackType.SegmentFrequentTick
        }

        bindings.hapticFeedback.value.performHapticFeedback(type)
    }

    private fun passedTick(
        from: Float,
        to: Float,
    ): Int {
        val candidate = when {
            to > from -> floor(to)
            else -> ceil(to)
        }
        val isPassed = candidate != from && candidate in min(from, to)..max(from, to)

        return when {
            isPassed -> candidate.toInt()
            else -> reportedTick
        }
    }
}

@Composable
internal fun rememberAdjustmentBarState(
    value: Float,
    scale: AdjustmentBarScale,
    bindings: RulerBindings,
): AdjustmentBarState {
    val currentScale = rememberUpdatedState(scale)
    val state = remember {
        AdjustmentBarState(
            initialTick = scale.tickOf(value = value),
            currentScale = currentScale,
            bindings = bindings,
        )
    }

    RulerSyncEffect(
        state = state,
        value = value,
    )

    return state
}
