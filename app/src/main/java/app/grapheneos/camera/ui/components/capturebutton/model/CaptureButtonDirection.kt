package app.grapheneos.camera.ui.components.capturebutton.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.LayoutDirection

internal enum class CaptureButtonDirection {
    Start,
    End,
    Up,
    Down,
    ;

    internal fun unitVector(layoutDirection: LayoutDirection): Offset {
        val startSign = when (layoutDirection) {
            LayoutDirection.Ltr -> -1f
            LayoutDirection.Rtl -> 1f
        }

        return when (this) {
            Start -> Offset(x = startSign, y = 0f)
            End -> Offset(x = -startSign, y = 0f)
            Up -> Offset(x = 0f, y = -1f)
            Down -> Offset(x = 0f, y = 1f)
        }
    }
}
