package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var clicks = 0

    @Test
    fun captureButton_tapped_callsOnClick() {
        setCaptureButton()

        button().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun captureButton_tappedInQuickSuccession_callsOnClickForEveryTap() {
        setCaptureButton()

        button().performClick()
        button().performClick()
        button().performClick()

        assertEquals(3, clicks)
    }

    @Test
    fun captureButton_disabled_ignoresTaps() {
        setCaptureButton(enabled = false)

        button().performClick()

        button().assertIsNotEnabled()
        assertEquals(0, clicks)
    }

    @Test
    fun captureButton_isAnnouncedAsAButton() {
        setCaptureButton()

        button().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun captureButton_withAnIcon_announcesOnlyTheCallersDescription() {
        setCaptureButton(icon = ICON)

        button().assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(DESCRIPTION),
            ),
        )
    }

    private fun setCaptureButton(
        enabled: Boolean = true,
        icon: ImageVector? = null,
    ) {
        composeRule.setContent {
            CameraTheme {
                CaptureButton(
                    onClick = { clicks += 1 },
                    core = CaptureButtonCore.Disc,
                    modifier = Modifier.semantics { contentDescription = DESCRIPTION },
                    enabled = enabled,
                    icon = icon,
                )
            }
        }
    }

    private fun button(): SemanticsNodeInteraction {
        return composeRule.onNodeWithContentDescription(DESCRIPTION)
    }

    private companion object {
        private const val DESCRIPTION = "Capture"

        private val ICON = ImageVector
            .Builder(
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
            .build()
    }
}
