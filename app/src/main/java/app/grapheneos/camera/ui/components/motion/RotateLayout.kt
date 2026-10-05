package app.grapheneos.camera.ui.components.motion

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun Modifier.rotateLayout(rotation: () -> Float): Modifier {
    return layout { measurable, constraints ->
        val side = max(constraints.maxWidth, constraints.maxHeight)
        val placeable = measurable.measure(
            Constraints(
                maxWidth = side,
                maxHeight = side,
            ),
        )
        val radians = Math.toRadians(rotation().toDouble())
        val cosine = abs(cos(radians)).toFloat()
        val sine = abs(sin(radians)).toFloat()
        val width = (placeable.width * cosine + placeable.height * sine).roundToInt()
        val height = (placeable.width * sine + placeable.height * cosine).roundToInt()

        layout(
            width = width.coerceIn(constraints.minWidth, constraints.maxWidth),
            height = height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {
            placeable.placeWithLayer(
                x = (width - placeable.width) / 2,
                y = (height - placeable.height) / 2,
            ) {
                rotationZ = rotation()
            }
        }
    }
}
