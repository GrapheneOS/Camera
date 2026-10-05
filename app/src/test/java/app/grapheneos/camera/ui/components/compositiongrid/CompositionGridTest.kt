package app.grapheneos.camera.ui.components.compositiongrid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CompositionGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aTap_reachesTheImageUnderTheGrid() {
        var taps by mutableIntStateOf(0)

        composeRule.setContent {
            CameraTheme {
                Box(
                    modifier = Modifier
                        .size(size = VIEWFINDER_SIZE)
                        .testTag(VIEWFINDER_TAG),
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { taps += 1 },
                    )
                    CompositionGrid(
                        pattern = CompositionGridPattern.Thirds,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(VIEWFINDER_TAG).performTouchInput { click() }

        assertEquals(1, taps)
    }

    @Test
    fun grid_addsNothingToTheAccessibilityTree() {
        composeRule.setContent {
            CameraTheme {
                Box(
                    modifier = Modifier
                        .size(size = VIEWFINDER_SIZE)
                        .testTag(VIEWFINDER_TAG),
                ) {
                    CompositionGrid(
                        pattern = CompositionGridPattern.GoldenRatio,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(VIEWFINDER_TAG).onChildren().assertCountEquals(0)
    }

    private companion object {
        private const val VIEWFINDER_TAG = "viewfinder"
        private val VIEWFINDER_SIZE = 120.dp
    }
}
