package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.LensFacing
import org.junit.Assert.assertEquals
import org.junit.Test

class LensFacingTest {

    @Test
    fun supportedOrOpposite_ofASupportedLens_keepsIt() {
        val lensFacing = LensFacing.FRONT.supportedOrOpposite { true }

        assertEquals(LensFacing.FRONT, lensFacing)
    }

    @Test
    fun supportedOrOpposite_ofAnUnsupportedLens_takesTheOtherOne() {
        val lensFacing = LensFacing.BACK.supportedOrOpposite { it == LensFacing.FRONT }

        assertEquals(LensFacing.FRONT, lensFacing)
    }
}
