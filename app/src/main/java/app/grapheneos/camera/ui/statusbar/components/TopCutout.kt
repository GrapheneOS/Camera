package app.grapheneos.camera.ui.statusbar.components

import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates

internal fun topCutout(
    view: View,
    coordinates: LayoutCoordinates,
): Rect {
    val bounds = view.rootWindowInsets?.displayCutout?.boundingRectTop

    return when {
        bounds == null || bounds.isEmpty -> Rect.Zero
        else -> Rect(
            offset = coordinates.windowToLocal(
                Offset(
                    x = bounds.left.toFloat(),
                    y = bounds.top.toFloat(),
                ),
            ),
            size = Size(
                width = bounds.width().toFloat(),
                height = bounds.height().toFloat(),
            ),
        )
    }
}
