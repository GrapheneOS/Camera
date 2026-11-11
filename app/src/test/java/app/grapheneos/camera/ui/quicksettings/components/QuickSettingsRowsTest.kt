package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.theme.CameraTheme
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickChoiceUiModel
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class QuickSettingsRowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var showsTimer by mutableStateOf(true)

    @Test
    fun timerDisappearing_keepsOptionsInPlaceHorizontally() {
        setContent()
        val initialBounds = optionBounds()

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle { showsTimer = false }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(MID_TRANSITION_MILLIS)

            assertHorizontalBounds(expected = initialBounds, actual = optionBounds())
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        composeRule.onNodeWithContentDescription(OPTION_DESCRIPTION).assertDoesNotExist()
    }

    @Test
    fun timerAppearing_keepsOptionsInPlaceHorizontally() {
        showsTimer = false
        setContent()
        val intermediateBounds: Rect

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle { showsTimer = true }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(MID_TRANSITION_MILLIS)

            intermediateBounds = optionBounds()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        assertHorizontalBounds(expected = optionBounds(), actual = intermediateBounds)
    }

    private fun optionBounds(): Rect {
        return composeRule.onNodeWithContentDescription(OPTION_DESCRIPTION)
            .fetchSemanticsNode().boundsInRoot
    }

    private fun assertHorizontalBounds(
        expected: Rect,
        actual: Rect,
    ) {
        assertEquals(expected.left, actual.left, 0.5f)
        assertEquals(expected.right, actual.right, 0.5f)
    }

    private fun setContent() {
        composeRule.setContent {
            CameraTheme {
                Column(
                    modifier = Modifier
                        .width(400.dp)
                        .fillMaxHeight(),
                ) {
                    QuickSettingsRows(
                        uiState = QuickSettingsUiState(
                            selfTimer = TIMER.takeIf { showsTimer },
                        ),
                        rowHighlight = remember { RowHighlightState() },
                        onAction = {},
                    )
                }
            }
        }
    }

    private companion object {
        const val MID_TRANSITION_MILLIS = 80L
        const val OPTION_DESCRIPTION = "Timer, 10s"
        val TIMER = QuickChoiceUiModel(
            selected = Duration.ZERO,
            options = persistentListOf(Duration.ZERO, 3.seconds, 5.seconds, 10.seconds),
        )
    }
}
