package app.grapheneos.camera.ui.previewoverlays.screen.delegate

import app.grapheneos.camera.data.camera.model.CameraFocusEvent
import app.grapheneos.camera.data.camera.model.CameraFocusRequest
import app.grapheneos.camera.data.camera.session.FocusControl
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.sound.model.CameraSound
import app.grapheneos.camera.domain.sound.usecase.PlayCameraSound
import app.grapheneos.camera.ui.components.focusindicator.model.FocusIndicatorAppearance
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.mapper.PreviewOverlaysUiStateMapperImpl
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusPhase
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusPress
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusSelection
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewContentState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.FocusUiModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FocusDelegateTest {

    private val events = MutableSharedFlow<CameraFocusEvent>()
    private val focusControl = mockk<FocusControl>(relaxed = true) {
        every { events } returns this@FocusDelegateTest.events
    }
    private val playCameraSound = mockk<PlayCameraSound>(relaxed = true)
    private val delegate = FocusDelegateImpl(
        focusControl = focusControl,
        playCameraSound = playCameraSound,
    )
    private val mapper = PreviewOverlaysUiStateMapperImpl()
    private val framing = PreviewOverlaysState(
        settings = CameraSettings(focusTimeoutSeconds = 3),
        previewContent = PreviewContentState(isPreviewStreaming = true),
    )
    private val stateHolder = StateHolder(initial = framing)

    @Test
    fun focusAfterChange_hidesTheRingAndDropsThePressWhenThePreviewIsInterrupted() {
        val selection = FocusSelection(requestId = 1, x = 10f, y = 20f)
        val shown = framing.copy(
            focus = FocusState(
                selection = selection,
                press = FocusPress.NewPoint(requestId = 2, x = 30f, y = 40f),
            ),
        )
        val content = requireNotNull(shown.previewContent)
        val interruptions = listOf(
            shown.copy(previewContent = content.copy(isPreviewStreaming = false)),
            shown.copy(previewContent = content.copy(isTransitionFrameVisible = true)),
            shown.copy(previewContent = content.copy(isCaptureResultVisible = true)),
            shown.copy(previewContent = content.copy(mode = CameraMode.VIDEO)),
            shown.copy(previewContent = content.copy(mode = CameraMode.QR_SCAN)),
        )

        interruptions.forEach { current ->
            val focus = delegate.focusAfterChange(previous = shown, current = current)

            assertEquals(
                current.toString(),
                FocusState(selection = selection.copy(visible = false)),
                focus,
            )
        }
    }

    @Test
    fun focusAfterChange_keepsTheRingWhileThePreviewContinues() {
        val shown = framing.copy(
            focus = FocusState(selection = FocusSelection(requestId = 1, x = 10f, y = 20f)),
        )
        val updates = listOf(
            shown,
            shown.copy(isPreviewFlashVisible = true),
            shown.copy(settings = shown.settings.copy(focusTimeoutSeconds = 0)),
        )

        updates.forEach { current ->
            val focus = delegate.focusAfterChange(previous = shown, current = current)

            assertEquals(current.toString(), shown.focus, focus)
        }
    }

    @Test
    fun screenPaused_cancelsMeteringAndForgetsTheRing() {
        runTest {
            trackFocus()
            startFocus()

            delegate.onScreenPaused()

            verify(exactly = 1) { focusControl.cancel() }
            assertEquals(FocusState(), stateHolder.state.value.focus)
        }
    }

    @Test
    fun successfulFocus_keepsTheRingUntilCameraCancellation() {
        runTest {
            trackFocus()
            startFocus()

            assertEquals(FocusSelection(requestId = 1, x = 10f, y = 20f), selection())

            events.emit(CameraFocusEvent.Focused(requestId = 1))
            advanceTimeBy(60.seconds)

            assertEquals(FocusPhase.Focused, selection().phase)
            assertTrue(selection().visible)
            verify(exactly = 0) { focusControl.cancel() }

            events.emit(CameraFocusEvent.Cancelled(requestId = 1))

            assertFalse(selection().visible)
        }
    }

    @Test
    fun newerFocus_ignoresOldResultsAndCancellation() {
        runTest {
            trackFocus()
            startFocus()
            startFocus(x = 100f, y = 200f)

            events.emit(CameraFocusEvent.Focused(requestId = 1))
            events.emit(CameraFocusEvent.Cancelled(requestId = 1))

            assertEquals(FocusSelection(requestId = 2, x = 100f, y = 200f), selection())

            events.emit(CameraFocusEvent.Focused(requestId = 2))

            assertEquals(FocusPhase.Focused, selection().phase)
        }
    }

    @Test
    fun cancelledRing_isNotRestoredByALateSuccess() {
        runTest {
            trackFocus()
            startFocus()

            delegate.cancelFocus()
            events.emit(CameraFocusEvent.Focused(requestId = 1))

            verify(exactly = 1) { focusControl.cancel() }
            assertFalse(selection().visible)
        }
    }

    @Test
    fun tappingTheRing_cancelsMeteringInsteadOfStartingAnotherRequest() {
        runTest {
            trackFocus()
            startFocus()

            press(x = 12f, y = 21f)
            delegate.onPreviewTapped()

            verify(exactly = 1) { focusControl.cancel() }
            verify(exactly = 1) { focusControl.focus(any()) }
            assertFalse(selection().visible)
        }
    }

    @Test
    fun tappingOutsideTheRing_usesTheConfiguredTimeout() {
        runTest {
            trackFocus()
            startFocus()

            startFocus(x = 100f, y = 200f)

            verify(exactly = 1) {
                focusControl.focus(
                    CameraFocusRequest(id = 2, x = 100f, y = 200f, autoCancelAfter = 3.seconds),
                )
            }
        }
    }

    @Test
    fun holdingTheRing_locksTheSameRequestAndKeepsTheLockWhenFocusCompletes() {
        runTest {
            trackFocus()
            startFocus()

            press(x = 10f, y = 20f)
            delegate.onPreviewLongPressed()
            events.emit(CameraFocusEvent.Locked(requestId = 1))
            events.emit(CameraFocusEvent.Focused(requestId = 1))

            verify(exactly = 1) { focusControl.lock() }
            assertEquals(
                FocusSelection(requestId = 1, x = 10f, y = 20f, phase = FocusPhase.Locked),
                selection(),
            )
        }
    }

    @Test
    fun holdingARingThatWasCancelledMeanwhile_locksANewRequestAtThePressedPoint() {
        runTest {
            trackFocus()
            startFocus()

            press(x = 10f, y = 20f)
            events.emit(CameraFocusEvent.Cancelled(requestId = 1))
            delegate.onPreviewLongPressed()

            verify(exactly = 0) { focusControl.lock() }
            verify(exactly = 1) { focusControl.focus(lockedRequest(id = 2)) }
        }
    }

    @Test
    fun holdingANewPoint_commitsTheProvisionalRingAsALockedRequest() {
        runTest {
            trackFocus()

            press(x = 10f, y = 20f)
            val provisional = requireNotNull(ring())
            delegate.onPreviewLongPressed()

            assertTrue(provisional.visible)
            verify(exactly = 1) {
                focusControl.focus(lockedRequest(id = provisional.requestId))
            }
            assertEquals(provisional, ring())
            assertNull(stateHolder.state.value.focus.press)
        }
    }

    @Test
    fun aConfirmedTap_keepsTheProvisionalRingIdentity() {
        runTest {
            trackFocus()

            press(x = 10f, y = 20f)
            val provisional = requireNotNull(ring())
            delegate.onPreviewTapped()

            assertEquals(provisional, ring())
            assertEquals(provisional.requestId, selection().requestId)
        }
    }

    @Test
    fun draggingToOpenSettings_doesNotStartMeteringOrPlayASound() {
        runTest {
            trackFocus()

            press(x = 10f, y = 20f)
            delegate.onPreviewGestureStarted()
            delegate.onPreviewTapped()

            verify(exactly = 0) { focusControl.focus(any()) }
            coVerify(exactly = 0) { playCameraSound(any()) }
            assertNull(ring())
        }
    }

    @Test
    fun dragging_restoresThePreviousRingWithItsLatestPhase() {
        runTest {
            trackFocus()
            startFocus()

            press(x = 100f, y = 200f)
            events.emit(CameraFocusEvent.Focused(requestId = 1))
            delegate.onPreviewGestureStarted()

            assertEquals(
                FocusUiModel(
                    requestId = 1,
                    x = 10f,
                    y = 20f,
                    appearance = FocusIndicatorAppearance.Focused,
                    visible = true,
                ),
                ring(),
            )
            verify(exactly = 1) { focusControl.focus(any()) }
            verify(exactly = 0) { focusControl.cancel() }
        }
    }

    @Test
    fun aPreviousRingCancelledDuringAPressIsNotRestoredByADrag() {
        runTest {
            trackFocus()
            startFocus()

            press(x = 100f, y = 200f)
            events.emit(CameraFocusEvent.Cancelled(requestId = 1))
            delegate.onPreviewGestureStarted()

            assertFalse(requireNotNull(ring()).visible)
            verify(exactly = 0) { focusControl.cancel() }
        }
    }

    @Test
    fun aPressBeforeAModeChange_isNotCommittedAfterIt() {
        runTest {
            trackFocus()

            press(x = 10f, y = 20f)
            changePreview { content -> content.copy(mode = CameraMode.VIDEO) }
            delegate.onPreviewTapped()

            verify(exactly = 0) { focusControl.focus(any()) }
        }
    }

    @Test
    fun focusStart_playsTheSoundOnlyForPhotos() {
        runTest {
            trackFocus()

            startFocus()
            changePreview { content -> content.copy(mode = CameraMode.VIDEO) }
            startFocus(x = 100f, y = 200f)

            coVerify(exactly = 1) { playCameraSound(CameraSound.FOCUS_START) }
        }
    }

    @Test
    fun captureFlashTemporarilyHidesTheLockWithoutLosingIt() {
        runTest {
            trackFocus()
            startFocus()
            events.emit(CameraFocusEvent.Locked(requestId = 1))

            changePreview(isPreviewFlashVisible = true)

            assertFalse(requireNotNull(ring()).visible)

            changePreview(isPreviewFlashVisible = false)

            assertTrue(requireNotNull(ring()).visible)
            assertEquals(FocusPhase.Locked, selection().phase)
            verify(exactly = 0) { focusControl.cancel() }
        }
    }

    @Test
    fun cancellationDuringFlashIsNotUndoneWhenTheFlashEnds() {
        runTest {
            trackFocus()
            startFocus()

            changePreview(isPreviewFlashVisible = true)
            events.emit(CameraFocusEvent.Cancelled(requestId = 1))
            changePreview(isPreviewFlashVisible = false)

            assertFalse(selection().visible)
        }
    }

    private fun changePreview(
        isPreviewFlashVisible: Boolean = stateHolder.state.value.isPreviewFlashVisible,
        transform: (PreviewContentState) -> PreviewContentState = { it },
    ) {
        stateHolder.update { previous ->
            val current = previous.copy(
                previewContent = previous.previewContent?.let(transform),
                isPreviewFlashVisible = isPreviewFlashVisible,
            )
            current.copy(
                focus = delegate.focusAfterChange(previous = previous, current = current),
            )
        }
    }

    private fun selection(): FocusSelection {
        return requireNotNull(stateHolder.state.value.focus.selection)
    }

    private fun ring(): FocusUiModel? {
        return mapper.map(stateHolder.state.value).focus
    }

    private fun TestScope.trackFocus() {
        delegate.bind(stateHolder)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { delegate.trackFocus() }
    }

    private suspend fun startFocus(
        x: Float = 10f,
        y: Float = 20f,
    ) {
        press(x = x, y = y)
        delegate.onPreviewTapped()
        events.emit(CameraFocusEvent.Started(requestId = selection().requestId))
    }

    private fun press(
        x: Float,
        y: Float,
    ) {
        delegate.onPreviewPressed(x = x, y = y, hitRadius = HIT_RADIUS)
    }

    private fun lockedRequest(id: Long): CameraFocusRequest {
        return CameraFocusRequest(
            id = id,
            x = 10f,
            y = 20f,
            autoCancelAfter = 3.seconds,
            isLocked = true,
        )
    }

    private companion object {
        private const val HIT_RADIUS = 30f
    }
}
