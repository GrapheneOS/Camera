package app.grapheneos.camera.ui.components.adjustmentbar

import androidx.compose.animation.core.EaseInOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs

@Immutable
internal class AdjustmentBarTint(
    startTint: Color,
    endTint: Color,
) {

    private val startGlow = glowBrush(tint = startTint)
    private val endGlow = glowBrush(tint = endTint)

    fun draw(
        drawScope: DrawScope,
        position: Float,
        lastTick: Int,
    ) {
        val middle = lastTick / 2f
        val glow = when {
            position < middle -> startGlow
            else -> endGlow
        }

        if (glow != null) {
            drawScope.drawRoundRect(
                brush = glow,
                alpha = strength(
                    position = position,
                    middle = middle,
                ),
                cornerRadius = CornerRadius(drawScope.size.height / 2),
            )
        }
    }

    companion object {
        private const val CENTER = 0.5f
        private const val GLOW_REACH = 0.4f
        private const val GLOW_STOPS = 21

        /** Eased, so the middle of the range has no visible edge where the tint starts. */
        internal fun strength(
            position: Float,
            middle: Float,
        ): Float {
            return EaseInOut.transform((abs(position - middle) / middle).coerceIn(0f, 1f))
        }

        private fun glowBrush(tint: Color): Brush? {
            return when {
                tint.alpha == 0f -> null
                else -> Brush.horizontalGradient(colorStops = glowStops(tint = tint))
            }
        }

        private fun glowStops(tint: Color): Array<Pair<Float, Color>> {
            return Array(GLOW_STOPS) { index ->
                val fraction = index / (GLOW_STOPS - 1f)
                val distance = (abs(fraction - CENTER) / GLOW_REACH).coerceIn(0f, 1f)
                val falloff = 1f - EaseInOut.transform(distance)

                fraction to tint.copy(alpha = tint.alpha * falloff)
            }
        }
    }
}

@Composable
internal fun rememberAdjustmentBarTint(colors: AdjustmentBarColors): AdjustmentBarTint {
    return remember(colors.startTint, colors.endTint) {
        AdjustmentBarTint(
            startTint = colors.startTint,
            endTint = colors.endTint,
        )
    }
}
