package app.grapheneos.camera.ui.components.pillselector.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.Modifier

internal fun Modifier.pillSelectorInput(
    selection: PillSelectorSelection,
    enabled: Boolean,
    onItemSelected: (index: Int) -> Unit,
): Modifier {
    return draggable(
        state = selection.dragState,
        orientation = Orientation.Horizontal,
        enabled = enabled,
        interactionSource = selection.dragInteractions,
        onDragStarted = { selection.startDrag() },
        onDragStopped = { selection.endDrag(onReleased = onItemSelected) },
    )
}
