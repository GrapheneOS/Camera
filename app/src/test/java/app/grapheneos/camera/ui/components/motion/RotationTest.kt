package app.grapheneos.camera.ui.components.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class RotationTest {

    @Test
    fun shortestTurn_threeQuarters_turnsBackInstead() {
        assertEquals(-90f, shortestTurn(from = 0f, to = 270f))
    }

    @Test
    fun shortestTurn_fromAnAccumulatedAngle_takesTheShortWay() {
        assertEquals(10f, shortestTurn(from = 350f, to = 0f))
    }

    @Test
    fun shortestTurn_sameAngle_staysStill() {
        assertEquals(0f, shortestTurn(from = 720f, to = 0f))
    }
}
