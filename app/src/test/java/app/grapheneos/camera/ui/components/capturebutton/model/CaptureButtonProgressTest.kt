package app.grapheneos.camera.ui.components.capturebutton.model

import org.junit.Test

class CaptureButtonProgressTest {

    @Test(expected = IllegalArgumentException::class)
    fun determinate_fractionAboveOne_isRejected() {
        CaptureButtonProgress.Determinate(fraction = 1.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun determinate_fractionNotANumber_isRejected() {
        CaptureButtonProgress.Determinate(fraction = Float.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun segmented_withoutSegments_isRejected() {
        CaptureButtonProgress.Segmented(
            segments = 0,
            filled = 0,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun segmented_filledBeyondSegments_isRejected() {
        CaptureButtonProgress.Segmented(
            segments = 3,
            filled = 4,
        )
    }
}
