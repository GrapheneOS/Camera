package app.grapheneos.camera.ui.components.focusindicator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.focusindicator.model.FocusIndicatorAppearance
import app.grapheneos.camera.ui.core.CameraTheme
import app.grapheneos.camera.ui.core.PREVIEW_LOCK_ICON
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FocusIndicatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var taps by mutableIntStateOf(0)

    @Test
    fun aTapOnTheRing_reachesTheImageUnderIt() {
        setContent(tappable = true)

        composeRule.onNodeWithTag(VIEWFINDER_TAG).performTouchInput { click() }

        assertEquals(1, taps)
    }

    @Test
    fun indicator_addsNothingToTheAccessibilityTree() {
        setContent(tappable = false)

        composeRule.onNodeWithTag(VIEWFINDER_TAG).onChildren().assertCountEquals(0)
    }

    private fun setContent(tappable: Boolean) {
        composeRule.setContent {
            CameraTheme {
                Box(
                    modifier = Modifier
                        .size(size = VIEWFINDER_SIZE)
                        .testTag(VIEWFINDER_TAG),
                ) {
                    if (tappable) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { taps += 1 },
                        )
                    }
                    FocusIndicator(
                        visible = true,
                        appearance = FocusIndicatorAppearance.Locked,
                        lockIcon = PREVIEW_LOCK_ICON,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }

    private companion object {
        private const val VIEWFINDER_TAG = "viewfinder"
        private val VIEWFINDER_SIZE = 160.dp
    }
}
