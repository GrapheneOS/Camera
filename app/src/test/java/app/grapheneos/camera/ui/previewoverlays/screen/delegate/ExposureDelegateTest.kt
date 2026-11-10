package app.grapheneos.camera.ui.previewoverlays.screen.delegate

import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.session.ExposureControl
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.ExposureAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.ExposureState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusPhase
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusPress
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusSelection
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewContentState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewControl
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExposureDelegateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val supportedExposure = CameraExposure(
        compensationIndex = 0,
        compensationRange = -12..12,
        compensationStep = 1f / 6f,
        isCompensationSupported = true,
    )
    private val cameraExposure = MutableStateFlow<CameraExposure?>(supportedExposure)
    private val control = mockk<ExposureControl> {
        every { exposure } returns cameraExposure
        every { setCompensationIndex(any()) } answers {
            cameraExposure.value = cameraExposure.value?.copy(compensationIndex = firstArg())
        }
    }
    private val stateHolder = StateHolder(
        initial = PreviewOverlaysState(
            previewContent = PreviewContentState(isPreviewStreaming = true),
        ),
    )
    private val delegate = ExposureDelegateImpl(control).apply { bind(stateHolder) }

    private val state: PreviewOverlaysState
        get() = stateHolder.state.value

    @Test
    fun onlyANewFocusSelection_opensExposure() {
        runTest {
            trackExposure()
            val press = FocusPress.NewPoint(
                requestId = 1,
                x = 10f,
                y = 20f,
            )
            stateHolder.update { it.copy(focus = FocusState(press = press)) }
            assertFalse(state.isExposureVisible())

            selectFocus(requestId = 1)
            assertTrue(state.isExposureVisible())
            assertEquals(supportedExposure, state.exposure.value)

            stateHolder.update { it.copy(activeControl = PreviewControl.Zoom) }
            val selection = requireNotNull(state.focus.selection)
            val focused = selection.copy(phase = FocusPhase.Focused)
            stateHolder.update { it.copy(focus = FocusState(selection = focused)) }
            assertFalse(state.isExposureVisible())

            selectFocus(requestId = 2)
            assertTrue(state.isExposureVisible())
        }
    }

    @Test
    fun refocusingBeforeTimeout_givesTheNewSelectionTwoFullSeconds() {
        runTest {
            backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
                delegate.trackExposure()
            }
            selectFocus(requestId = 1)
            runCurrent()
            advanceTimeBy(1.seconds)

            stateHolder.update { it.copy(activeControl = PreviewControl.Zoom) }
            selectFocus(requestId = 2)
            runCurrent()
            advanceTimeBy(2.seconds)

            assertTrue(state.isExposureVisible())
            runCurrent()
            assertFalse(state.isExposureVisible())
        }
    }

    @Test
    fun holdingExposure_defersTheTimeoutUntilRelease() {
        runTest {
            trackExposure()
            selectFocus()
            delegate.onAction(Action.PressChanged(isPressed = true))
            advanceTimeBy(5.seconds)
            assertTrue(state.isExposureVisible())

            delegate.onAction(Action.PressChanged(isPressed = false))
            advanceTimeBy(2.seconds)

            assertTrue(state.isExposureVisible())
            runCurrent()
            assertFalse(state.isExposureVisible())
        }
    }

    @Test
    fun reportingTheSameValue_restartsTheTimeout() {
        runTest {
            trackExposure()
            selectFocus()
            advanceTimeBy(1.seconds)
            delegate.onAction(Action.ValueChanged(value = 0f))
            advanceTimeBy(2.seconds)

            assertTrue(state.isExposureVisible())
            runCurrent()
            assertFalse(state.isExposureVisible())
        }
    }

    @Test
    fun exposureValues_areRoundedToCameraStepsAndClampedToTheRange() {
        runTest {
            trackExposure()
            selectFocus()

            delegate.onAction(Action.ValueChanged(value = 0.45f))
            delegate.onAction(Action.ValueChanged(value = 100f))
            delegate.onAction(Action.ValueChanged(value = -100f))
            delegate.onAction(Action.ValueChanged(value = Float.NaN))

            verifyOrder {
                control.setCompensationIndex(3)
                control.setCompensationIndex(12)
                control.setCompensationIndex(-12)
            }
            verify(exactly = 3) { control.setCompensationIndex(any()) }
            assertEquals(-12, state.exposure.value?.compensationIndex)
        }
    }

    @Test
    fun cameraCorrection_updatesTheVisiblePanelAndDetachClearsIt() {
        runTest {
            trackExposure()
            selectFocus()
            delegate.onAction(Action.ValueChanged(value = 0.5f))

            cameraExposure.value = supportedExposure
            assertEquals(0, state.exposure.value?.compensationIndex)
            assertTrue(state.isExposureVisible())

            cameraExposure.value = null

            assertEquals(PreviewControl.Zoom, state.activeControl)
            assertNull(state.exposure.value)
            assertFalse(state.exposure.isAdjusted)
        }
    }

    @Test
    fun returningToZero_keepsResetAvailableUntilAnExplicitResetSucceeds() {
        runTest {
            trackExposure()
            selectFocus()
            delegate.onAction(Action.ValueChanged(value = 0.5f))
            delegate.onAction(Action.ValueChanged(value = 0f))
            advanceTimeBy(3.seconds)
            selectFocus(requestId = 2)
            assertTrue(state.exposure.isAdjusted)

            delegate.onAction(Action.ValueChanged(value = 1f))
            delegate.onAction(Action.ResetClicked)

            assertEquals(0, state.exposure.value?.compensationIndex)
            assertFalse(state.exposure.isAdjusted)
        }
    }

    @Test
    fun rejectedReset_keepsTheCameraValueAndTheResetButton() {
        runTest {
            cameraExposure.value = supportedExposure.copy(compensationIndex = 3)
            every { control.setCompensationIndex(0) } just runs
            trackExposure()
            selectFocus()

            delegate.onAction(Action.ResetClicked)

            verify(exactly = 1) { control.setCompensationIndex(0) }
            assertEquals(3, state.exposure.value?.compensationIndex)
            assertTrue(state.exposure.isAdjusted)
        }
    }

    @Test
    fun unavailableCameraExposure_doesNotOpenOrAcceptInput() {
        runTest {
            trackExposure()
            val unavailable = listOf(
                null,
                supportedExposure.copy(isCompensationSupported = false),
                supportedExposure.copy(compensationRange = 0..0),
                supportedExposure.copy(compensationStep = 0f),
                supportedExposure.copy(compensationStep = -1f),
                supportedExposure.copy(compensationStep = Float.NaN),
                supportedExposure.copy(compensationStep = Float.POSITIVE_INFINITY),
            )
            unavailable.forEachIndexed { index, exposure ->
                cameraExposure.value = exposure
                selectFocus(requestId = index.toLong())

                assertEquals(exposure.toString(), PreviewControl.Zoom, state.activeControl)
                delegate.onAction(Action.ValueChanged(value = 1f))
                delegate.onAction(Action.ResetClicked)
            }

            verify(exactly = 0) { control.setCompensationIndex(any()) }
        }
    }

    @Test
    fun qrPreview_keepsThePanelClosedForANewFocus() {
        runTest {
            trackExposure()
            stateHolder.update { state ->
                state.copy(previewContent = state.previewContent?.copy(mode = CameraMode.QR_SCAN))
            }

            selectFocus()

            assertEquals(PreviewControl.Zoom, state.activeControl)
        }
    }

    @Test
    fun changingCameraOrInterruptingTheStream_forgetsTheAdjustment() {
        runTest {
            trackExposure()
            selectFocus()
            delegate.onAction(Action.ValueChanged(value = 0.5f))
            delegate.onAction(Action.PressChanged(isPressed = true))
            val previous = state
            val content = requireNotNull(previous.previewContent)
            val changes = listOf(
                content.copy(mode = CameraMode.VIDEO),
                content.copy(lensFacing = LensFacing.FRONT),
                content.copy(isPreviewStreaming = false),
                content.copy(isTransitionFrameVisible = true),
            )

            changes.forEach { changed ->
                val exposure = delegate.exposureAfterChange(
                    previous = previous,
                    current = previous.copy(previewContent = changed),
                )

                assertNull(changed.toString(), exposure.value)
                assertFalse(changed.toString(), exposure.isAdjusted)
                assertFalse(changed.toString(), exposure.isPressed)
            }
        }
    }

    @Test
    fun screenPaused_forgetsTheAdjustmentAndIgnoresLateInput() {
        runTest {
            trackExposure()
            selectFocus()
            delegate.onAction(Action.ValueChanged(value = 0.5f))
            delegate.onAction(Action.PressChanged(isPressed = true))

            delegate.onScreenPaused()
            delegate.onAction(Action.PressChanged(isPressed = false))
            delegate.onAction(Action.ValueChanged(value = 1f))

            assertEquals(ExposureState(), state.exposure)
            assertEquals(PreviewControl.Zoom, state.activeControl)
            verify(exactly = 1) { control.setCompensationIndex(any()) }
        }
    }

    private fun TestScope.trackExposure() {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { delegate.trackExposure() }
    }

    private fun selectFocus(requestId: Long = 1) {
        val selection = FocusSelection(
            requestId = requestId,
            x = 10f,
            y = 20f,
        )
        stateHolder.update { it.copy(focus = FocusState(selection = selection)) }
    }
}
