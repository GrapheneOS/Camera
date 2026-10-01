package app.grapheneos.camera.ui.components.capturebutton.model

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureButtonProgressTest {

    @Test(expected = IllegalArgumentException::class)
    fun segmented_withoutSegments_isRejected() {
        CaptureButtonProgress.Segmented(
            segments = 0,
            fraction = { 0f },
        )
    }

    @Test
    fun determinate_fractionOutOfRange_isReportedWithinRange() {
        val progress = CaptureButtonProgress.Determinate(fraction = { 1.5f })

        assertEquals(
            ProgressBarRangeInfo(
                current = 1f,
                range = 0f..1f,
            ),
            progress.rangeInfo,
        )
    }
}
