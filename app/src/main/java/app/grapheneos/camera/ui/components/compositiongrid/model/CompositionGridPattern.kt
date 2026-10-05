package app.grapheneos.camera.ui.components.compositiongrid.model

import kotlin.math.roundToInt

private const val INVERSE_GOLDEN_RATIO = 0.618034f

internal enum class CompositionGridPattern(
    internal val fractions: List<Float>,
) {
    Thirds(
        fractions = equalParts(count = 3),
    ),
    Quarters(
        fractions = equalParts(count = 4),
    ),
    GoldenRatio(
        fractions = listOf(1f - INVERSE_GOLDEN_RATIO, INVERSE_GOLDEN_RATIO),
    ),
    ;

    internal fun lineOffsets(length: Int): FloatArray {
        val lastPixel = length - 1

        return FloatArray(fractions.size) { index ->
            (fractions[index] * lastPixel).roundToInt() + PIXEL_CENTER
        }
    }

    private companion object {
        private const val PIXEL_CENTER = 0.5f
    }
}

private fun equalParts(count: Int): List<Float> {
    return List(count - 1) { index ->
        (index + 1f) / count
    }
}
