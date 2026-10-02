package app.grapheneos.camera.ui.components.modeselector.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection

internal fun Modifier.modeSelectorInput(
    state: ModeSelectorState,
    enabled: Boolean,
    layoutDirection: LayoutDirection,
): Modifier {
    return draggable(
        state = state,
        orientation = Orientation.Horizontal,
        enabled = enabled,
        reverseDirection = layoutDirection == LayoutDirection.Rtl,
        onDragStopped = { state.release() },
    )
}
