package app.grapheneos.camera.ui.components.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentFillTest {

    @Test
    fun segmentFill_half_countsTheCapsAsPartOfTheLength() {
        val filled = SegmentFill.of(fill = 0.5f, sweep = 100f, cap = 10f)

        assertEquals(SegmentFill(sweep = 40f, alpha = 1f), filled)
    }

    @Test
    fun segmentFill_shorterThanItsCaps_fadesTheDotInsteadOfKeepingItWhole() {
        val filled = SegmentFill.of(fill = 0.125f, sweep = 100f, cap = 10f)

        assertEquals(SegmentFill(sweep = 0f, alpha = 0.75f), filled)
    }

    @Test
    fun segmentFill_withoutCaps_scalesTheSweep() {
        val filled = SegmentFill.of(fill = 0.25f, sweep = 100f, cap = 0f)

        assertEquals(SegmentFill(sweep = 25f, alpha = 1f), filled)
    }
}
