package app.grapheneos.camera.ui.components.levelindicator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun of_pointedStraightDown_isLevelAtAnyRoll() {
        assertEquals(LevelZone.Level, LevelZone.of(roll = 120f, pitch = -89.5f))
        assertEquals(LevelZone.Away, LevelZone.of(roll = 120f, pitch = -80f))
    }

    @Test
    fun isTopDown_switchesWithAGap_soItDoesNotFlickerAtTheEdge() {
        assertFalse(LevelZone.isTopDown(pitch = -55f, wasTopDown = false))
        assertTrue(LevelZone.isTopDown(pitch = -60f, wasTopDown = false))
        assertTrue(LevelZone.isTopDown(pitch = -55f, wasTopDown = true))
        assertFalse(LevelZone.isTopDown(pitch = 50f, wasTopDown = true))
    }
}
