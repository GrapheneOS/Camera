package app.grapheneos.camera.ui.previewoverlays.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.grapheneos.camera.ui.core.theme.CameraTheme
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.LevelUiState
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewLevelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reachingLevel_playsOneDesignSystemHaptic() {
        val level = mutableStateOf(
            LevelUiState(
                visible = true,
                rollDegrees = 3f,
            ),
        )
        val hapticFeedback = mockk<HapticFeedback>(relaxed = true)
        setContent(level = level, hapticFeedback = hapticFeedback)

        level.value = level.value.copy(rollDegrees = 0f)
        composeRule.waitForIdle()

        verify(exactly = 1) { hapticFeedback.performHapticFeedback(any()) }
    }

    @Test
    fun hidingAnUnalignedLevelAndShowingItAligned_doesNotPlayAHaptic() {
        val level = mutableStateOf(LevelUiState(visible = true, rollDegrees = 3f))
        val hapticFeedback = mockk<HapticFeedback>(relaxed = true)
        setContent(level = level, hapticFeedback = hapticFeedback)

        level.value = LevelUiState()
        composeRule.waitForIdle()
        level.value = LevelUiState(visible = true)
        composeRule.waitForIdle()

        verify(exactly = 0) { hapticFeedback.performHapticFeedback(any()) }
    }

    private fun setContent(
        level: State<LevelUiState>,
        hapticFeedback: HapticFeedback,
    ) {
        composeRule.setContent {
            CameraTheme {
                CompositionLocalProvider(LocalHapticFeedback provides hapticFeedback) {
                    PreviewLevel(level = level)
                }
            }
        }
    }
}
