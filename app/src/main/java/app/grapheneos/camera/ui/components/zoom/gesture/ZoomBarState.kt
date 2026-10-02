package app.grapheneos.camera.ui.components.zoom.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.grapheneos.camera.ui.components.ruler.RulerBindings
import app.grapheneos.camera.ui.components.ruler.RulerState
import app.grapheneos.camera.ui.components.ruler.RulerSyncEffect
import app.grapheneos.camera.ui.components.zoom.ZoomBarScale
import kotlin.math.abs
import kotlin.math.sign

@Stable
internal class ZoomBarState(
    initialValue: Float,
    private val currentScale: State<ZoomBarScale>,
    bindings: RulerBindings,
) : RulerState(
    initialPosition = currentScale.value.position(value = initialValue),
    currentScale = currentScale,
    bindings = bindings,
) {

    private var reportedValue by mutableFloatStateOf(initialValue)
    private var detentTravel = 0f

    override fun step(ticks: Int): Boolean {
        val scale = currentScale.value
        val position = scale.position(value = bindings.value.value)

        return moveTo(value = scale.value(position = position + ticks))
    }

    override fun moveTo(value: Float): Boolean {
        val range = currentScale.value.valueRange
        val target = value.coerceIn(range.start, range.endInclusive)
        val moves = target != bindings.value.value

        if (moves) {
            commit(value = target)
        }

        return moves
    }

    override fun isReported(value: Float): Boolean {
        return value == reportedValue
    }

    override suspend fun release() {
        detentTravel = 0f
        super.release()
    }

    override suspend fun syncTo(value: Float) {
        animatePosition(
            target = currentScale.value.position(value = value),
            onStart = {
                detentTravel = 0f
                reportedValue = value
            },
        )
    }

    override fun dragTarget(
        from: Float,
        ticks: Float,
    ): Float {
        val scale = currentScale.value
        val to = (from + ticks).coerceIn(0f, scale.lastTick.toFloat())
        val stop = when {
            scale.isStop(position = from) -> from
            else -> scale.firstStop(from = from, to = to)
        }

        return when (stop) {
            null -> to
            else -> holdAt(
                stop = stop,
                travel = detentTravel + to - stop,
            )
        }
    }

    override fun onDragged(
        from: Float,
        to: Float,
    ) {
        if (to != from) {
            val scale = currentScale.value

            reportValue(value = scale.value(position = to))
            if (scale.firstStop(from = from, to = to) != null) {
                bindings.hapticFeedback.value.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
    }

    private fun holdAt(
        stop: Float,
        travel: Float,
    ): Float {
        val isHeld = abs(travel) <= DETENT_TICKS

        detentTravel = when {
            isHeld -> travel
            else -> 0f
        }

        return when {
            isHeld -> stop
            else -> stop + travel - DETENT_TICKS * sign(travel)
        }
    }

    private fun reportValue(value: Float) {
        reportedValue = value
        report(value = value)
    }

    private companion object {
        private const val DETENT_TICKS = 1f
    }
}

@Composable
internal fun rememberZoomBarState(
    value: Float,
    scale: ZoomBarScale,
    bindings: RulerBindings,
): ZoomBarState {
    val currentScale = rememberUpdatedState(scale)
    val state = remember {
        ZoomBarState(
            initialValue = value,
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
