package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.positionChanged

internal fun PointerEvent.consumePositionChanges() {
    changes
        .filter { it.positionChanged() }
        .forEach { it.consume() }
}
