package app.grapheneos.camera.ui.components.thumbnailbutton

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverScaleTest {

    @Test
    fun upright_keepsTheImageAsItIs() {
        assertEquals(1f, coverScale(degrees = 0f))
        assertEquals(1f, coverScale(degrees = 90f))
        assertEquals(1f, coverScale(degrees = -90f))
    }

    @Test
    fun halfwayThroughATurn_growsByTheDiagonal() {
        assertEquals(sqrt(2f), coverScale(degrees = 45f))
    }

    @Test
    fun anyAngle_coversTheTurnedSquare() {
        assertEquals(COS_30 + SIN_30, coverScale(degrees = 30f))
    }

    private companion object {
        private const val SIN_30 = 0.5f
        private val COS_30 = sqrt(3f) / 2
    }
}
