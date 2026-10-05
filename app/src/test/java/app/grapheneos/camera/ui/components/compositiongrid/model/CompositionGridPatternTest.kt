package app.grapheneos.camera.ui.components.compositiongrid.model

import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositionGridPatternTest {

    @Test
    fun thirds_splitTheImageIntoEqualParts() {
        assertEquals(listOf(1f / 3f, 2f / 3f), CompositionGridPattern.Thirds.fractions)
    }

    @Test
    fun quarters_splitTheImageIntoEqualParts() {
        assertEquals(listOf(0.25f, 0.5f, 0.75f), CompositionGridPattern.Quarters.fractions)
    }

    @Test
    fun goldenRatio_dividesEachSideInTheGoldenRatio() {
        val (smaller, larger) = CompositionGridPattern.GoldenRatio.fractions

        assertEquals(GOLDEN_RATIO, 1f / larger)
        assertEquals(GOLDEN_RATIO, larger / smaller, TOLERANCE)
    }

    @Test
    fun everyPattern_isSymmetric() {
        CompositionGridPattern.entries.forEach { pattern ->
            val fractions = pattern.fractions

            fractions.indices.forEach { index ->
                val mirrored = fractions[fractions.lastIndex - index]

                assertEquals(1f, fractions[index] + mirrored)
            }
        }
    }

    @Test
    fun lineOffsets_fallOnPixelCenters() {
        CompositionGridPattern.entries.forEach { pattern ->
            pattern.lineOffsets(length = LENGTH).forEach { offset ->
                assertEquals(PIXEL_CENTER, offset - floor(offset))
            }
        }
    }

    @Test
    fun lineOffsets_mirrorAcrossTheCenter() {
        CompositionGridPattern.entries.forEach { pattern ->
            val offsets = pattern.lineOffsets(length = LENGTH)

            (0 until offsets.size / 2).forEach { index ->
                val mirrored = offsets[offsets.lastIndex - index]

                assertEquals(LENGTH.toFloat(), offsets[index] + mirrored)
            }
        }
    }

    @Test
    fun lineOffsets_placeThirdsOnTheNearestPixels() {
        assertEquals(
            listOf(360.5f, 719.5f),
            CompositionGridPattern.Thirds.lineOffsets(length = LENGTH).toList(),
        )
    }

    private companion object {
        private const val GOLDEN_RATIO = 1.618034f
        private const val TOLERANCE = 0.0001f
        private const val PIXEL_CENTER = 0.5f
        private const val LENGTH = 1080
    }
}
