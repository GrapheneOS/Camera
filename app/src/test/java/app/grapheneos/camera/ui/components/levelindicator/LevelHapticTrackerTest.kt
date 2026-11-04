package app.grapheneos.camera.ui.components.levelindicator

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelHapticTrackerTest {

    @Test
    fun reachesLevel_onlyOnTheWayIn() {
        val tracker = LevelHapticTracker(initialZone = LevelZone.Away)

        assertEquals(
            listOf(false, true, false),
            tracker.reaches(LevelZone.Near, LevelZone.Level, LevelZone.Level),
        )
    }

    @Test
    fun reachesLevel_startingLevel_waitsForTheNextApproach() {
        val tracker = LevelHapticTracker(initialZone = LevelZone.Level)

        assertEquals(
            listOf(false, false, true),
            tracker.reaches(LevelZone.Level, LevelZone.Away, LevelZone.Level),
        )
    }

    @Test
    fun reachesLevel_wobblingAtTheEdge_waitsUntilTiltedWellAway() {
        val tracker = LevelHapticTracker(initialZone = LevelZone.Away)

        assertEquals(
            listOf(true, false, false, false, true),
            tracker.reaches(
                LevelZone.Level,
                LevelZone.Near,
                LevelZone.Level,
                LevelZone.Away,
                LevelZone.Level,
            ),
        )
    }

    private fun LevelHapticTracker.reaches(vararg zones: LevelZone): List<Boolean> {
        return zones.map { zone -> reachesLevel(zone = zone) }
    }
}
