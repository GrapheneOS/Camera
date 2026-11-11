package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.data.camera.model.HighFrameRateLimit
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.ui.core.theme.CameraTheme
import app.grapheneos.camera.ui.quicksettings.screen.model.OptionLimit
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickChoiceUiModel
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class QuickSettingsRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var unavailable by mutableStateOf(false)
    private var shownLimit: OptionLimit? = null

    @Test
    fun unavailableExplanation_keepsTheRowHeightAndCanBeOpened() {
        checkExplanation(fontScale = 1f)
    }

    @Test
    fun unavailableExplanation_withLargeText_keepsTheRowHeightAndCanBeOpened() {
        checkExplanation(fontScale = 2f)
    }

    private fun checkExplanation(fontScale: Float) {
        setContent(fontScale = fontScale)
        val row = composeRule.onNodeWithTag(ROW_TAG)
        val height = row.fetchSemanticsNode().boundsInRoot.height

        composeRule.runOnIdle { unavailable = true }

        assertEquals(height, row.fetchSemanticsNode().boundsInRoot.height)
        composeRule.onNodeWithContentDescription("$ROW_LABEL, 60 fps").assertIsNotEnabled()
        composeRule.onNodeWithText(ROW_LABEL).performClick()
        assertEquals(LIMIT, shownLimit)

        composeRule.runOnIdle { unavailable = false }

        assertEquals(height, row.fetchSemanticsNode().boundsInRoot.height)
    }

    private fun setContent(fontScale: Float) {
        composeRule.setContent {
            val density = Density(
                density = LocalDensity.current.density,
                fontScale = fontScale,
            )

            CompositionLocalProvider(LocalDensity provides density) {
                CameraTheme {
                    Box(modifier = Modifier.width(400.dp)) {
                        QuickSettingsRow(
                            label = ROW_LABEL,
                            setting = QuickChoiceUiModel(
                                selected = VideoFrameRate.FPS_30,
                                options = VideoFrameRate.entries.toImmutableList(),
                                unavailable = when {
                                    unavailable -> persistentSetOf(VideoFrameRate.FPS_60)
                                    else -> persistentSetOf()
                                },
                                limit = LIMIT.takeIf { unavailable },
                            ),
                            onSelect = {},
                            onLimitClick = { shownLimit = it },
                            valueLabel = { "${it.framesPerSecond} fps" },
                            modifier = Modifier.testTag(ROW_TAG),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val ROW_TAG = "frameRate"
        const val ROW_LABEL = "Frame rate"
        val LIMIT = OptionLimit.HighFrameRate(limit = HighFrameRateLimit.RESOLUTION)
    }
}
