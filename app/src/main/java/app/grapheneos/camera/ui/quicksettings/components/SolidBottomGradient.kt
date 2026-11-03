package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush

/** Fades from [topColor] into [color], reaching it [solidHeight] pixels above the bottom. */
internal data class SolidBottomGradient(
    private val topColor: Color,
    private val color: Color,
    private val solidHeight: Float,
) : ShaderBrush() {

    override fun createShader(size: Size): Shader {
        val solidFrom = when {
            size.height > 0f -> ((size.height - solidHeight) / size.height).coerceIn(0f, 1f)
            else -> 0f
        }

        return LinearGradientShader(
            from = Offset.Zero,
            to = Offset(x = 0f, y = size.height),
            colors = listOf(topColor, color),
            colorStops = listOf(0f, solidFrom),
        )
    }
}
