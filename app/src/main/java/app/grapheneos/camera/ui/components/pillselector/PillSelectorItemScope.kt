package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.ColorProducer
import app.grapheneos.camera.ui.components.pillselector.gesture.PillSelectorSelection
import kotlin.math.abs

@Stable
internal class PillSelectorItemScope(
    private val index: Int,
    private val selection: PillSelectorSelection,
    colors: State<PillSelectorColors>,
) {

    val contentColor = ColorProducer { colors.value.contentColor }

    fun emphasis(): Float {
        return (1f - abs(selection.value - index)).coerceIn(0f, 1f)
    }
}
