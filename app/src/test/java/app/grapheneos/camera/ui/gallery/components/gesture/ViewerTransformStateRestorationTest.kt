package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewerTransformStateRestorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var pageKey by mutableStateOf(FIRST_PAGE)
    private lateinit var transformState: ViewerTransformState

    @Test
    fun recreation_whileZoomed_keepsTheZoom() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            transformState = rememberViewerTransformState(pageKey = pageKey)
        }
        composeRule.runOnIdle {
            transformState.updateLayout(
                containerSize = SCREEN,
                imageSize = SCREEN,
                canZoom = true,
            )
            transformState.applyDoubleTap(position = Offset.Zero)
        }

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle { assertEquals(2.5f, transformState.scale) }
    }

    @Test
    fun anotherPage_startsFitted() {
        composeRule.setContent {
            transformState = rememberViewerTransformState(pageKey = pageKey)
        }
        composeRule.runOnIdle {
            transformState.updateLayout(
                containerSize = SCREEN,
                imageSize = SCREEN,
                canZoom = true,
            )
            transformState.applyDoubleTap(position = Offset.Zero)
        }

        pageKey = SECOND_PAGE

        composeRule.runOnIdle { assertEquals(1f, transformState.scale) }
    }

    private companion object {
        const val FIRST_PAGE = "content://media/external/images/media/1"
        const val SECOND_PAGE = "content://media/external/images/media/2"
        val SCREEN = Size(width = 100f, height = 100f)
    }
}
