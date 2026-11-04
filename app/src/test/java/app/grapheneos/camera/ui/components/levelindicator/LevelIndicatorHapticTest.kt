package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import app.grapheneos.camera.ui.core.theme.CameraTheme
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LevelIndicatorHapticTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var mode by mutableStateOf(LevelIndicatorMode.Horizon)
    private var roll by mutableFloatStateOf(3f)
    private var pitch by mutableFloatStateOf(0f)
    private val hapticFeedback = mockk<HapticFeedback>(relaxed = true)

    @Test
    fun reachingLevel_playsTheDefaultHaptic() {
        setContent()

        roll = 0f
        composeRule.waitForIdle()

        verify(exactly = 1) { hapticFeedback.performHapticFeedback(any()) }
    }

    @Test
    fun startingLevel_doesNotPlayAHaptic() {
        roll = 0f
        setContent()

        verify(exactly = 0) { hapticFeedback.performHapticFeedback(any()) }
    }

    @Test
    fun tremblingNearLevel_doesNotRepeatTheHaptic() {
        setContent()
        roll = 0f
        composeRule.waitForIdle()

        listOf(0.3f, -0.2f, 0.4f, -0.4f, 0f).forEach { value ->
            roll = value
            composeRule.waitForIdle()
        }

        verify(exactly = 1) { hapticFeedback.performHapticFeedback(any()) }
    }

    @Test
    fun tiltingAwayAndReturning_playsAnotherHaptic() {
        setContent()
        roll = 0f
        composeRule.waitForIdle()
        roll = 3f
        composeRule.waitForIdle()
        roll = 0f
        composeRule.waitForIdle()

        verify(exactly = 2) { hapticFeedback.performHapticFeedback(any()) }
    }

    @Test
    fun crosshairs_doNotPlayTheHorizonHapticWhenPitchPassesZero() {
        mode = LevelIndicatorMode.Crosshairs
        pitch = 70f
        setContent()

        roll = 0f
        pitch = 0f
        composeRule.waitForIdle()

        verify(exactly = 0) { hapticFeedback.performHapticFeedback(any()) }
    }

    @Test
    fun crosshairs_playOneHapticWhenPointingStraightDown() {
        mode = LevelIndicatorMode.Crosshairs
        pitch = -70f
        setContent()

        pitch = -90f
        composeRule.waitForIdle()

        verify(exactly = 1) { hapticFeedback.performHapticFeedback(any()) }
    }

    private fun setContent() {
        composeRule.setContent {
            CameraTheme {
                CompositionLocalProvider(LocalHapticFeedback provides hapticFeedback) {
                    LevelIndicator(
                        mode = mode,
                        roll = { roll },
                        pitch = { pitch },
                    )
                }
            }
        }
    }
}
