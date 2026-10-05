package app.grapheneos.camera.ui.components.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RotateLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun upright_keepsItsSize() {
        setContent(degrees = 0f)

        composeRule.onNodeWithTag(TAG)
            .assertWidthIsEqualTo(WIDTH)
            .assertHeightIsEqualTo(HEIGHT)
    }

    @Test
    fun aQuarterTurn_swapsWidthAndHeight() {
        setContent(degrees = QUARTER_TURN)

        composeRule.onNodeWithTag(TAG)
            .assertWidthIsEqualTo(HEIGHT)
            .assertHeightIsEqualTo(WIDTH)
    }

    private fun setContent(degrees: Float) {
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .testTag(TAG)
                    .rotateLayout(rotation = { degrees }),
            ) {
                Box(modifier = Modifier.size(width = WIDTH, height = HEIGHT))
            }
        }
    }

    private companion object {
        private const val TAG = "rotated"
        private const val QUARTER_TURN = 90f
        private val WIDTH = 100.dp
        private val HEIGHT = 40.dp
    }
}
