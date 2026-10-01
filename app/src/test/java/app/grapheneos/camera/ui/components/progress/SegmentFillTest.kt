package app.grapheneos.camera.ui.components.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentFillTest {

    @Test
    fun segmentFill_half_countsTheCapsAsPartOfTheLength() {
        val filled = segmentFill(fill = 0.5f, sweepAngle = 100f, capAngle = 10f)

        assertEquals(SegmentFill(sweepAngle = 40f, alpha = 1f), filled)
    }

    @Test
    fun segmentFill_shorterThanItsCaps_fadesTheDotInsteadOfKeepingItWhole() {
        val filled = segmentFill(fill = 0.125f, sweepAngle = 100f, capAngle = 10f)

        assertEquals(SegmentFill(sweepAngle = 0f, alpha = 0.75f), filled)
    }

    @Test
    fun segmentFill_withoutCaps_scalesTheSweep() {
        val filled = segmentFill(fill = 0.25f, sweepAngle = 100f, capAngle = 0f)

        assertEquals(SegmentFill(sweepAngle = 25f, alpha = 1f), filled)
    }
}
