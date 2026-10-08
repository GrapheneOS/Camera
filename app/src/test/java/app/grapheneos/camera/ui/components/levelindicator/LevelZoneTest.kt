package app.grapheneos.camera.ui.components.levelindicator

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelZoneTest {

    @Test
    fun of_bothLinesMet_isLevel() {
        assertEquals(LevelZone.Level, LevelZone.of(roll = 0.4f, pitch = -0.9f))
    }

    @Test
    fun of_levelRollButPitched_isNotLevel() {
        assertEquals(LevelZone.Near, LevelZone.of(roll = 0f, pitch = 1.5f))
        assertEquals(LevelZone.Away, LevelZone.of(roll = 0f, pitch = 10f))
    }
}
