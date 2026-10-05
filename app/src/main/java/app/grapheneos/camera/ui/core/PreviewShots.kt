package app.grapheneos.camera.ui.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint

private const val PREVIEW_SHOT_SIZE = 96

internal fun previewShot(
    top: Color,
    bottom: Color,
): ImageBitmap {
    val image = ImageBitmap(
        width = PREVIEW_SHOT_SIZE,
        height = PREVIEW_SHOT_SIZE,
    )
    val paint = Paint().apply {
        shader = LinearGradientShader(
            from = Offset.Zero,
            to = Offset(x = 0f, y = PREVIEW_SHOT_SIZE.toFloat()),
            colors = listOf(top, bottom),
        )
    }

    Canvas(image).drawRect(
        left = 0f,
        top = 0f,
        right = PREVIEW_SHOT_SIZE.toFloat(),
        bottom = PREVIEW_SHOT_SIZE.toFloat(),
        paint = paint,
    )

    return image
}
