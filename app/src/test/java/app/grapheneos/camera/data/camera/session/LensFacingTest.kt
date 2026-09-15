package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraSelector
import org.junit.Assert.assertEquals
import org.junit.Test

class LensFacingTest {

    @Test
    fun supportedLensFacing_ofASupportedLens_keepsIt() {
        val lensFacing = supportedLensFacing(preferred = CameraSelector.LENS_FACING_FRONT) { true }

        assertEquals(CameraSelector.LENS_FACING_FRONT, lensFacing)
    }

    @Test
    fun supportedLensFacing_ofAnUnsupportedLens_takesTheOtherOne() {
        val lensFacing = supportedLensFacing(preferred = CameraSelector.LENS_FACING_BACK) {
            it == CameraSelector.LENS_FACING_FRONT
        }

        assertEquals(CameraSelector.LENS_FACING_FRONT, lensFacing)
    }
}
