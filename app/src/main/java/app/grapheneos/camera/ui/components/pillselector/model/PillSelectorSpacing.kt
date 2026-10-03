package app.grapheneos.camera.ui.components.pillselector.model

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class PillSelectorSpacing(
    internal val gap: Dp,
) {
    Spaced(gap = 4.dp),
    Joined(gap = 0.dp),
}
