package app.grapheneos.camera.ui.components.levelindicator

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelTrackerTest {

    @Test
    fun reachesLevel_onlyOnTheWayIn() {
        val tracker = LevelTracker(initialZone = LevelZone.Away)

        assertEquals(
            listOf(false, true, false),
            tracker.reaches(LevelZone.Near, LevelZone.Level, LevelZone.Level),
        )
    }

    @Test
    fun reachesLevel_startingLevel_waitsForTheNextApproach() {
        val tracker = LevelTracker(initialZone = LevelZone.Level)

        assertEquals(
            listOf(false, false, true),
            tracker.reaches(LevelZone.Level, LevelZone.Away, LevelZone.Level),
        )
    }

    @Test
    fun reachesLevel_wobblingAtTheEdge_waitsUntilTiltedWellAway() {
        val tracker = LevelTracker(initialZone = LevelZone.Away)

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

    private fun LevelTracker.reaches(vararg zones: LevelZone): List<Boolean> {
        return zones.map { zone -> reachesLevel(zone = zone) }
    }
}
