package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.ui.core.theme.CameraTheme
import app.grapheneos.camera.ui.quicksettings.screen.QuickSettingsContent
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickChoiceUiModel
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsInsets
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
    fun hidingSettingsButton_keepsTheHeaderHeight() {
        var visible by mutableStateOf(true)
        composeRule.setContent {
            CameraTheme {
                QuickSettingsHeader(
                    isVideoMode = true,
                    settingsButtonVisible = visible,
                    modifier = Modifier.testTag("header"),
                    enabled = true,
                    onSettingsClick = {},
                    onSettingsLongClick = {},
                )
            }
        }

        val button = composeRule.onNodeWithContentDescription("Camera settings")
        button.assertIsEnabled()
        val header = composeRule.onNodeWithTag("header")
        val height = header.fetchSemanticsNode().boundsInRoot.height
        composeRule.runOnIdle { visible = false }

        assertEquals(height, header.fetchSemanticsNode().boundsInRoot.height)
        button.assertDoesNotExist()
    }

    @Test
    fun changingFlashOptions_disablesTheOutgoingLayout() {
        var options by mutableStateOf(persistentListOf(FlashMode.OFF, FlashMode.LOW_LIGHT_BOOST))
        composeRule.setContent {
            CameraTheme {
                Column(modifier = Modifier.width(400.dp)) {
                    QuickSettingsRows(
                        uiState = QuickSettingsUiState(
                            flash = QuickChoiceUiModel(
                                selected = FlashMode.OFF,
                                options = options,
                            ),
                        ),
                        enabled = true,
                        rowHighlight = remember { RowHighlightState() },
                        onAction = {},
                    )
                }
            }
        }

        val outgoingOption = composeRule.onNodeWithContentDescription("Flash Mode, Low light")
        outgoingOption.assertIsEnabled()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle { options = persistentListOf(FlashMode.OFF, FlashMode.ON) }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(MID_TRANSITION_MILLIS)

            outgoingOption.assertIsNotEnabled()
            composeRule.onNodeWithContentDescription("Flash Mode, On").assertIsEnabled()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        outgoingOption.assertDoesNotExist()
    }

    @Test
    fun closingSheet_disablesTheControlsWhileTheyAreStillVisible() {
        val state = QuickSettingsSheetState(
            settleVelocity = 125f,
            minFlingDistance = 25f,
            isShown = true,
        )
        composeRule.setContent {
            CameraTheme {
                Box(modifier = Modifier.width(400.dp)) {
                    QuickSettingsContent(
                        uiState = QuickSettingsUiState(selfTimer = TIMER),
                        sheetState = state,
                        rowHighlight = remember { RowHighlightState() },
                        insets = QuickSettingsInsets(),
                        onSettle = {},
                        onAction = {},
                    )
                }
            }
        }

        val option = composeRule.onNodeWithContentDescription(OPTION_DESCRIPTION)
        option.assertIsEnabled()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle { state.show(false) }
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(MID_TRANSITION_MILLIS)
            composeRule.onNodeWithContentDescription("Camera settings").assertIsNotEnabled()
            option.assertIsNotEnabled()

            composeRule.runOnIdle { state.startDrag() }
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeByFrame()
            option.assertIsEnabled()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

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

            composeRule.onNodeWithContentDescription(OPTION_DESCRIPTION).assertIsNotEnabled()
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
                        enabled = true,
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
