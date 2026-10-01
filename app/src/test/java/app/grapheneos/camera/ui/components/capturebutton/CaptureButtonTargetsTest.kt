package app.grapheneos.camera.ui.components.capturebutton

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureButtonTargetsTest {

    @Test
    fun dockCoreSize_atTheTarget_fitsTheDockedSize() {
        assertEquals(40f, dockCoreSize(size = 60f, dockedSize = 40f, progress = 1f))
    }

    @Test
    fun dockCoreSize_halfway_shrinksHalfway() {
        assertEquals(50f, dockCoreSize(size = 60f, dockedSize = 40f, progress = 0.5f))
    }

    @Test
    fun dockCoreSize_smallerThanTheDock_neverGrows() {
        assertEquals(30f, dockCoreSize(size = 30f, dockedSize = 40f, progress = 1f))
    }
}
