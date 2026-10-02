package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs

@Stable
internal class PillSelectorItemScope(
    private val index: Int,
    private val selection: State<Float>,
    colors: State<PillSelectorColors>,
) {

    val contentColor = ColorProducer {
        lerp(
            start = colors.value.contentColor,
            stop = colors.value.selectedContentColor,
            fraction = emphasis(),
        )
    }

    fun emphasis(): Float {
        return (1f - abs(selection.value - index)).coerceIn(0f, 1f)
    }
}
