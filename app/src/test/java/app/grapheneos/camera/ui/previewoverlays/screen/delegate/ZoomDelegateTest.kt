package app.grapheneos.camera.ui.previewoverlays.screen.delegate

import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.camera.model.CameraZoomEvent
import app.grapheneos.camera.data.camera.session.ZoomControl
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.VolumeKeyAction
import app.grapheneos.camera.domain.camera.mapper.ZoomPresetsMapperImpl
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.model.input.ZoomDirection
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewContentState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
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
class ZoomDelegateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var cameraZoom: CameraZoom? = CameraZoom(
        zoomRatio = 1f,
        linearZoom = 0f,
        minZoomRatio = 0.5f,
        maxZoomRatio = 8f,
    )
    private val events = MutableSharedFlow<CameraZoomEvent>(extraBufferCapacity = 8)
    private var lensFacing = LensFacing.BACK
    private val control = mockk<ZoomControl>(relaxed = true) {
        every { zoom } answers { cameraZoom }
        every { events } returns this@ZoomDelegateTest.events
        every { lensFacing } answers { this@ZoomDelegateTest.lensFacing }
    }
    private val stateHolder = StateHolder(
        initial = PreviewOverlaysState(
            previewContent = PreviewContentState(isPreviewStreaming = true),
        ),
    )
    private val delegate = ZoomDelegateImpl(
        zoomControl = control,
        zoomPresetsMapper = ZoomPresetsMapperImpl(),
    )

    @Test
    fun startingCollection_readsTheCurrentCameraWithoutReplayingAnEvent() {
        runTest {
            trackZoom()

            assertEquals(cameraZoom, stateHolder.state.value.zoom.value)
            assertFalse(stateHolder.state.value.zoom.isExpanded)
        }
    }

    @Test
    fun zoomKey_stepsByOneExpandsAndCollapsesAfterTwoSeconds() {
        runTest {
            trackZoom()
            delegate.onZoomKeyPressed(ZoomDirection.IN)
            runCurrent()
            verify(exactly = 1) { control.setZoomRatio(2f) }
            assertTrue(stateHolder.state.value.zoom.isExpanded)

            advanceTimeBy(1999.milliseconds)
            assertTrue(stateHolder.state.value.zoom.isExpanded)
            advanceTimeBy(1.milliseconds)
            runCurrent()

            assertFalse(stateHolder.state.value.zoom.isExpanded)
        }
    }

    @Test
    fun zoomKey_outOfTheWideAngleCamera_stopsAtThePrimaryOne() {
        runTest {
            trackZoom()
            delegate.onRatioChanged(ratio = 0.6f)
            delegate.onZoomKeyPressed(ZoomDirection.IN)

            verify(exactly = 1) { control.setZoomRatio(1f) }
        }
    }

    @Test
    fun volumeKey_walksPresetsAndRangeEndpointsWhileHeld() {
        runTest {
            trackZoom()
            setVolumeKeyAction(VolumeKeyAction.ZOOM)

            delegate.onVolumeKeyPressed(ZoomDirection.IN)
            runCurrent()
            verify(exactly = 1) { control.setZoomRatio(2f) }
            advanceTimeBy(2.seconds)
            runCurrent()
            delegate.onVolumeKeyReleased()
            delegate.onVolumeKeyPressed(ZoomDirection.OUT)
            advanceTimeBy(2.seconds)
            runCurrent()

            verifyOrder {
                control.setZoomRatio(2f)
                control.setZoomRatio(8f)
                control.setZoomRatio(2f)
                control.setZoomRatio(1f)
                control.setZoomRatio(0.5f)
            }
            assertTrue(stateHolder.state.value.zoom.isExpanded)
        }
    }

    @Test
    fun volumeKeyReleased_takesNoFurtherStep() {
        runTest {
            trackZoom()
            setVolumeKeyAction(VolumeKeyAction.ZOOM)

            delegate.onVolumeKeyPressed(ZoomDirection.IN)
            runCurrent()
            delegate.onVolumeKeyReleased()
            advanceTimeBy(2.seconds)
            runCurrent()

            verify(exactly = 1) { control.setZoomRatio(any()) }
        }
    }

    @Test
    fun volumeKey_withAnotherSetting_leavesTheZoomAlone() {
        runTest {
            trackZoom()
            setVolumeKeyAction(VolumeKeyAction.SHUTTER)

            delegate.onVolumeKeyPressed(ZoomDirection.IN)
            advanceTimeBy(2.seconds)
            runCurrent()

            verify(exactly = 0) { control.setZoomRatio(any()) }
        }
    }

    @Test
    fun selectingAPreset_keepsThePillCollapsedUntilTheCameraAcknowledgesIt() {
        runTest {
            val acknowledged = CompletableDeferred<Unit>()
            coEvery { control.animateZoomRatio(2f) } coAnswers { acknowledged.await() }
            trackZoom()
            delegate.onPresetSelected(ratio = 2f)
            coVerify(exactly = 1) { control.animateZoomRatio(2f) }
            assertFalse(stateHolder.state.value.zoom.isExpanded)
            assertEquals(2f, stateHolder.state.value.zoom.pendingPreset?.ratio)
            cameraZoom = cameraZoom?.copy(zoomRatio = 2f)
            events.emit(CameraZoomEvent.Changed)
            runCurrent()
            acknowledged.complete(Unit)
            runCurrent()

            assertNull(stateHolder.state.value.zoom.pendingPreset)
            assertFalse(stateHolder.state.value.zoom.isExpanded)
        }
    }

    @Test
    fun newPresetAndManualInput_cancelThePreviousTransition() {
        runTest {
            val cancelled = mutableListOf<Float>()
            coEvery { control.animateZoomRatio(any()) } coAnswers {
                val ratio = firstArg<Float>()
                try {
                    awaitCancellation()
                } finally {
                    cancelled += ratio
                }
            }
            trackZoom()
            delegate.onPresetSelected(ratio = 2f)
            delegate.onPresetSelected(ratio = 8f)
            runCurrent()

            assertEquals(listOf(2f), cancelled)
            delegate.onRatioChanged(ratio = 1.5f)
            runCurrent()

            assertEquals(listOf(2f, 8f), cancelled)
            assertNull(stateHolder.state.value.zoom.pendingPreset)
            verify(exactly = 1) { control.setZoomRatio(1.5f) }
        }
    }

    @Test
    fun pausingDuringAPresetChange_doesNotCancelANewRequestAfterResume() {
        runTest {
            coEvery { control.animateZoomRatio(any()) } coAnswers { awaitCancellation() }
            val collection = trackZoom()
            delegate.onPresetSelected(ratio = 2f)
            val previous = stateHolder.state.value.zoom.pendingPreset
            collection.cancel()
            delegate.onScreenPaused()
            trackZoom()
            delegate.onPresetSelected(ratio = 2f)
            runCurrent()

            assertTrue(stateHolder.state.value.zoom.pendingPreset != previous)
            assertEquals(2f, stateHolder.state.value.zoom.pendingPreset?.ratio)
            coVerify(exactly = 2) { control.animateZoomRatio(2f) }
        }
    }

    @Test
    fun detachDuringAPresetTransition_keepsTheLastZoomUntilTheNextCameraLoads() {
        runTest {
            val detached = CompletableDeferred<Unit>()
            coEvery { control.animateZoomRatio(any()) } coAnswers {
                detached.await()
                throw CancellationException()
            }
            trackZoom()
            val previous = stateHolder.state.value.zoom.value
            delegate.onPresetSelected(ratio = 2f)

            cameraZoom = null
            detached.complete(Unit)
            runCurrent()

            assertNull(stateHolder.state.value.zoom.pendingPreset)
            assertEquals(previous, stateHolder.state.value.zoom.value)
        }
    }

    @Test
    fun ratioRequests_areClampedToTheAttachedCamerasRange() {
        runTest {
            trackZoom()
            delegate.onRatioChanged(ratio = 100f)
            delegate.onRatioChanged(ratio = 0.1f)

            verifyOrder {
                control.setZoomRatio(8f)
                control.setZoomRatio(0.5f)
            }
        }
    }

    @Test
    fun restingAFingerOnTheControl_holdsThePanelUntilTwoSecondsAfterRelease() {
        runTest {
            trackZoom()
            delegate.onExpanded()
            advanceTimeBy(1.seconds)
            delegate.onControlPressChanged(isPressed = true)
            advanceTimeBy(10.seconds)
            assertTrue(stateHolder.state.value.zoom.isExpanded)

            delegate.onControlPressChanged(isPressed = false)
            advanceTimeBy(1999.milliseconds)
            assertTrue(stateHolder.state.value.zoom.isExpanded)
            advanceTimeBy(1.milliseconds)
            runCurrent()

            assertFalse(stateHolder.state.value.zoom.isExpanded)
        }
    }

    @Test
    fun previewPinch_accumulatesRatiosAndHoldsThePanelUntilTheGestureEnds() {
        runTest {
            trackZoom()
            delegate.onPreviewPinchStarted()
            delegate.onPreviewPinchScaleChanged(scaleFactor = 1.5f)
            delegate.onPreviewPinchScaleChanged(scaleFactor = 0.5f)
            advanceTimeBy(10.seconds)

            verifyOrder {
                control.setZoomRatio(2.25f)
                control.setZoomRatio(0.5625f)
            }
            assertTrue(stateHolder.state.value.zoom.isExpanded)

            delegate.onPreviewPinchFinished()
            advanceTimeBy(2.seconds)
            runCurrent()

            assertFalse(stateHolder.state.value.zoom.isExpanded)
        }
    }

    @Test
    fun anotherInteractionAtTheSameRatio_restartsTheCollapseDelay() {
        runTest {
            trackZoom()
            delegate.onExpanded()
            advanceTimeBy(1.5.seconds)
            delegate.onRatioChanged(ratio = 1f)
            advanceTimeBy(1.seconds)
            assertTrue(stateHolder.state.value.zoom.isExpanded)

            advanceTimeBy(1.seconds)
            runCurrent()
            assertFalse(stateHolder.state.value.zoom.isExpanded)
        }
    }

    @Test
    fun aReplacedPreviewRestoredWithinOneDispatch_doesNotKeepTheOldTimer() {
        runTest {
            trackZoom(dispatcher = StandardTestDispatcher(testScheduler))
            runCurrent()
            delegate.onExpanded()
            runCurrent()
            advanceTimeBy(1.5.seconds)
            changePreview { state -> state.copy(isPreviewFlashVisible = true) }
            changePreview { state -> state.copy(isPreviewFlashVisible = false) }
            delegate.onExpanded()
            runCurrent()
            advanceTimeBy(1.seconds)
            assertTrue(stateHolder.state.value.zoom.isExpanded)

            advanceTimeBy(1.seconds)
            runCurrent()
            assertFalse(stateHolder.state.value.zoom.isExpanded)
        }
    }

    @Test
    fun replacedPreview_clearsAnActiveInteraction() {
        runTest {
            trackZoom()
            val content = requireNotNull(stateHolder.state.value.previewContent)
            val interruptions = listOf(
                stateHolder.state.value.copy(isPreviewFlashVisible = true),
                stateHolder.state.value.copy(
                    previewContent = content.copy(mode = CameraMode.QR_SCAN),
                ),
                stateHolder.state.value.copy(
                    previewContent = content.copy(isCaptureResultVisible = true),
                ),
            )
            interruptions.forEach { interruption ->
                stateHolder.update { state ->
                    state.copy(
                        zoom = state.zoom.copy(
                            isExpanded = true,
                            isControlPressed = true,
                        ),
                    )
                }
                changePreview { interruption.copy(zoom = it.zoom) }

                assertFalse(stateHolder.state.value.zoom.isExpanded)
                assertFalse(stateHolder.state.value.zoom.isControlPressed)
                delegate.onRatioChanged(ratio = 2f)
            }

            verify(exactly = 0) { control.setZoomRatio(any()) }
        }
    }

    @Test
    fun newlyAttachedCamera_keepsThePanelAndTheOngoingPinch() {
        runTest {
            trackZoom()
            delegate.onPreviewPinchStarted()
            cameraZoom = cameraZoom?.copy(
                minZoomRatio = 1f,
                maxZoomRatio = 4f,
            )
            events.emit(CameraZoomEvent.Loaded)
            runCurrent()

            assertEquals(cameraZoom, stateHolder.state.value.zoom.value)
            assertTrue(stateHolder.state.value.zoom.isExpanded)
            assertTrue(stateHolder.state.value.zoom.isPreviewPinching)
        }
    }

    @Test
    fun aPinchDuringAModeSwitch_keepsZoomingAndAppliesTheLatestRatioToTheNewBinding() {
        runTest {
            trackZoom()
            delegate.onPreviewPinchStarted()
            cameraZoom = null
            changePreview { state ->
                state.copy(
                    previewContent = state.previewContent?.copy(
                        mode = CameraMode.VIDEO,
                        isPreviewStreaming = false,
                        isTransitionFrameVisible = true,
                    ),
                )
            }
            delegate.onPreviewPinchScaleChanged(scaleFactor = 1.5f)
            delegate.onPreviewPinchScaleChanged(scaleFactor = 0.5f)
            verify(exactly = 0) { control.setZoomRatio(any()) }
            verify(exactly = 1) {
                control.rememberZoomRatio(
                    zoomRatio = 0.5625f,
                    lensFacing = LensFacing.BACK,
                )
            }
            assertEquals(0.5625f, stateHolder.state.value.zoom.value?.zoomRatio)

            cameraZoom = CameraZoom(
                zoomRatio = 0.5625f,
                linearZoom = 0f,
                minZoomRatio = 0.5f,
                maxZoomRatio = 4f,
            )
            events.emit(CameraZoomEvent.Loaded)
            runCurrent()

            assertEquals(0.5625f, stateHolder.state.value.zoom.value?.zoomRatio)
            assertTrue(stateHolder.state.value.zoom.isExpanded)
            assertTrue(stateHolder.state.value.zoom.isPreviewPinching)
            delegate.onRatioChanged(ratio = 4f)
            verify(exactly = 1) { control.setZoomRatio(4f) }
        }
    }

    @Test
    fun inputDuringALensSwitch_isAppliedToTheSelectedLensWithoutZoomingTheOldOne() {
        runTest {
            trackZoom()
            changePreview { state ->
                state.copy(
                    previewContent = state.previewContent?.copy(
                        lensFacing = LensFacing.FRONT,
                        isPreviewStreaming = false,
                        isTransitionFrameVisible = true,
                    ),
                )
            }
            delegate.onPresetSelected(ratio = 2f)
            delegate.onRatioChanged(ratio = 3f)
            events.emit(CameraZoomEvent.Changed)
            runCurrent()
            verify(exactly = 0) { control.setZoomRatio(any()) }
            coVerify(exactly = 0) { control.animateZoomRatio(any()) }
            assertEquals(3f, stateHolder.state.value.zoom.value?.zoomRatio)
            verify(exactly = 1) {
                control.rememberZoomRatio(
                    zoomRatio = 3f,
                    lensFacing = LensFacing.FRONT,
                )
            }

            lensFacing = LensFacing.FRONT
            cameraZoom = CameraZoom(
                zoomRatio = 3f,
                linearZoom = 0f,
                minZoomRatio = 1f,
                maxZoomRatio = 4f,
            )
            events.emit(CameraZoomEvent.Loaded)
            runCurrent()

            verify(exactly = 0) { control.setZoomRatio(any()) }
            assertEquals(3f, stateHolder.state.value.zoom.value?.zoomRatio)
        }
    }

    @Test
    fun unavailableRangeAndInvalidInput_doNotReachTheCamera() {
        runTest {
            trackZoom()
            delegate.onRatioChanged(ratio = Float.NaN)
            delegate.onRatioChanged(ratio = Float.POSITIVE_INFINITY)
            listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { factor ->
                delegate.onPreviewPinchScaleChanged(scaleFactor = factor)
            }
            cameraZoom = cameraZoom?.copy(
                minZoomRatio = 1f,
                maxZoomRatio = 1f,
            )
            events.emit(CameraZoomEvent.Loaded)
            runCurrent()
            delegate.onExpanded()
            delegate.onRatioChanged(ratio = 1f)

            assertFalse(stateHolder.state.value.zoom.isExpanded)
            verify(exactly = 0) { control.setZoomRatio(any()) }
        }
    }

    @Test
    fun pause_clearsThePanelAndKeepsTheZoom() {
        runTest {
            val collection = trackZoom()
            delegate.onRatioChanged(ratio = 2f)
            cameraZoom = cameraZoom?.copy(zoomRatio = 2f)
            delegate.onExpanded()
            collection.cancel()
            delegate.onScreenPaused()
            advanceTimeBy(3.seconds)
            assertEquals(2f, stateHolder.state.value.zoom.value?.zoomRatio)
            assertFalse(stateHolder.state.value.zoom.isExpanded)

            trackZoom()

            assertEquals(2f, stateHolder.state.value.zoom.value?.zoomRatio)
            assertFalse(stateHolder.state.value.zoom.isExpanded)
            verify(exactly = 1) { control.setZoomRatio(2f) }
        }
    }

    private fun setVolumeKeyAction(action: VolumeKeyAction) {
        stateHolder.update { state ->
            state.copy(settings = CameraSettings(volumeKeyAction = action))
        }
    }

    private fun changePreview(transform: (PreviewOverlaysState) -> PreviewOverlaysState) {
        stateHolder.update { previous ->
            val current = transform(previous)
            current.copy(
                zoom = delegate.zoomAfterChange(
                    previous = previous,
                    current = current,
                ),
            )
        }
    }

    private fun TestScope.trackZoom(
        dispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher,
    ): Job {
        delegate.bind(stateHolder)
        val collection = backgroundScope.launch(dispatcher) { delegate.trackZoom() }
        runCurrent()
        return collection
    }
}
