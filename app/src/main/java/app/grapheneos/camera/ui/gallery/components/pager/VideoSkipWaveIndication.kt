package app.grapheneos.camera.ui.gallery.components.pager

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.launch

private const val WAVE_DURATION_MILLIS = 500
private const val WAVE_ALPHA = 0.4f

private val WAVE_EASING = FastOutSlowInEasing

internal data class VideoSkipWaveIndication(
    private val color: Color,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return SkipWaveNode(
            interactionSource = interactionSource,
            color = color,
        )
    }
}

private class SkipWaveNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : Modifier.Node(),
    DrawModifierNode {

    private val waves = mutableStateListOf<SkipWave>()

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                if (interaction is PressInteraction.Press) {
                    coroutineScope.launch { play(interaction.pressPosition) }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        waves.forEach { wave -> drawWave(wave) }
    }

    private suspend fun play(position: Offset) {
        val wave = SkipWave(position)

        waves += wave
        try {
            wave.progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = WAVE_DURATION_MILLIS,
                    easing = LinearEasing,
                ),
            )
        } finally {
            waves -= wave
        }
    }

    private fun DrawScope.drawWave(wave: SkipWave) {
        val progress = wave.progress.value

        drawCircle(
            color = color,
            radius = farthestCornerFrom(wave.position) * WAVE_EASING.transform(progress),
            center = wave.position,
            alpha = WAVE_ALPHA * (1f - progress),
        )
    }

    private fun DrawScope.farthestCornerFrom(position: Offset): Float {
        val horizontal = max(position.x, size.width - position.x)
        val vertical = max(position.y, size.height - position.y)

        return hypot(horizontal, vertical)
    }
}

private class SkipWave(
    val position: Offset,
) {
    val progress = Animatable(0f)
}
