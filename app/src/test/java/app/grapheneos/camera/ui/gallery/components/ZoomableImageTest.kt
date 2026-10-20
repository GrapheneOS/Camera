package app.grapheneos.camera.ui.gallery.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZoomableImageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zoomChanges = mutableListOf<Boolean>()

    @Test
    fun recreation_whileZoomed_keepsTheZoom() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            CameraTheme {
                ZoomableImage(
                    image = ImageBitmap(width = 64, height = 48),
                    onTap = {},
                    onZoomChanged = { isZoomed -> zoomChanges += isZoomed },
                )
            }
        }

        composeRule.onRoot().performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        zoomChanges.clear()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onRoot().performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()

        assertEquals(listOf(true, false), zoomChanges)
    }
}
