package app.grapheneos.camera.ui.components.levelindicator

import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LevelZoneTest {

    @Test
    fun of_bothLinesMet_isLevel() {
        assertEquals(
            LevelZone.Level,
            LevelZone.of(mode = LevelIndicatorMode.Horizon, roll = 0.4f, pitch = -0.9f)
        )
    }

    @Test
    fun of_levelRollButPitched_isNotLevel() {
        assertEquals(
            LevelZone.Near,
            LevelZone.of(mode = LevelIndicatorMode.Horizon, roll = 0f, pitch = 1.5f)
        )
        assertEquals(
            LevelZone.Away,
            LevelZone.of(mode = LevelIndicatorMode.Horizon, roll = 0f, pitch = 10f)
        )
    }

    @Test
    fun of_pointedStraightDown_isLevelAtAnyRoll() {
        assertEquals(
            LevelZone.Level,
            LevelZone.of(
                mode = LevelIndicatorMode.Crosshairs,
                roll = 120f,
                pitch = -89.5f
            )
        )
        assertEquals(
            LevelZone.Away,
            LevelZone.of(
                mode = LevelIndicatorMode.Crosshairs,
                roll = 120f,
                pitch = -80f
            )
        )
    }

    @Test
    fun crosshairs_doNotTreatTheHorizonAsVerticalAlignment() {
        assertEquals(
            LevelZone.Away,
            LevelZone.of(mode = LevelIndicatorMode.Crosshairs, roll = 0f, pitch = 0f),
        )
    }

    @Test
    fun horizon_doesNotTreatVerticalPitchAsHorizonAlignment() {
        assertEquals(
            LevelZone.Away,
            LevelZone.of(mode = LevelIndicatorMode.Horizon, roll = 0f, pitch = 90f),
        )
    }
}
