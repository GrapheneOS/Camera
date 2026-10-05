package app.grapheneos.camera.ui.components.progress.model

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class RingProgressTest {

    @Test(expected = IllegalArgumentException::class)
    fun segmented_withoutSegments_isRejected() {
        RingProgress.Segmented(
            segments = 0,
            fraction = { 0f },
        )
    }

    @Test
    fun determinate_fractionOutOfRange_isReportedWithinRange() {
        val progress = RingProgress.Determinate(fraction = { 1.5f })

        assertEquals(
            ProgressBarRangeInfo(
                current = 1f,
                range = 0f..1f,
            ),
            progress.rangeInfo,
        )
    }
}
