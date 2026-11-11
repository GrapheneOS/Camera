package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.lerp
import kotlin.math.ceil
import kotlin.math.floor

internal class PillSelectorGeometry(
    itemCount: Int,
) {

    val lastIndex: Int
        get() {
            return slots.lastIndex
        }

    private var slots by mutableStateOf(List(itemCount) { Slot() })

    fun resize(itemCount: Int) {
        val previous = slots

        slots = List(itemCount) { index -> previous.getOrNull(index) ?: Slot() }
    }

    fun place(
        index: Int,
        start: Float,
        width: Float,
        contentStart: Float,
    ) {
        val slot = slots[index]

        slot.start = start
        slot.width = width
        slot.contentStart = contentStart
    }

    fun start(selection: Float): Float {
        return interpolate(selection = selection) { slot -> slot.start }
    }

    fun width(selection: Float): Float {
        return interpolate(selection = selection) { slot -> slot.width }
    }

    fun contentStart(index: Int): Float {
        return slots[index].contentStart
    }

    fun pitch(selection: Float): Float {
        if (lastIndex < 1) return 0f

        val from = floor(selection).toInt().coerceIn(0, lastIndex - 1)

        return slots[from + 1].start - slots[from].start
    }

    private inline fun interpolate(
        selection: Float,
        value: (Slot) -> Float,
    ): Float {
        val from = floor(selection).toInt().coerceIn(0, lastIndex)
        val to = ceil(selection).toInt().coerceIn(0, lastIndex)

        return lerp(
            start = value(slots[from]),
            stop = value(slots[to]),
            fraction = selection - from,
        )
    }

    private class Slot {
        var start by mutableFloatStateOf(0f)
        var width by mutableFloatStateOf(0f)
        var contentStart by mutableFloatStateOf(0f)
    }
}
