package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.mapper.SwipeOutcomeMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.input.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.state.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderModeStripState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderPreviewState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderModeSwitchDelegateTest {

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA).also { holder ->
        holder.update { state ->
            state.copy(
                showsCameraModeTabs = true,
                session = state.session.copy(availableModes = MODES),
                preview = ViewfinderPreviewState(isStreaming = true),
            )
        }
    }

    private val delegate = ViewfinderModeSwitchDelegateImpl(
        swipeOutcomeMapper = SwipeOutcomeMapperImpl(),
    ).also { it.bind(stateHolder) }

    @Test
    fun stripReleased_onAnotherMode_headsThereBehindTheLastFrame() {
        delegate.onStripReleased(CameraMode.VIDEO)

        assertEquals(
            ViewfinderModeStripState(
                pendingMode = CameraMode.VIDEO,
                selectionId = 1,
                isSwitchPending = true,
            ),
            state().modeStrip,
        )
        assertTrue(state().preview.isLastFrameShown)
    }

    @Test
    fun stripReleased_onTheCurrentMode_settlesWithoutTheLastFrame() {
        delegate.onStripReleased(CameraMode.CAMERA)

        assertEquals(CameraMode.CAMERA, state().modeStrip.pendingMode)
        assertFalse(state().modeStrip.isSwitchPending)
        assertFalse(state().preview.isLastFrameShown)
    }

    @Test
    fun stripDragged_staysDraggedUntilReleased() {
        delegate.onStripDragged()
        assertTrue(state().modeStrip.isDragged)

        delegate.onStripReleased(CameraMode.CAMERA)
        assertFalse(state().modeStrip.isDragged)
    }

    @Test
    fun stripTouchCancelled_endsTheDrag() {
        delegate.onStripDragged()

        delegate.onStripTouchCancelled()

        assertEquals(ViewfinderModeStripState(), state().modeStrip)
    }

    @Test
    fun stripReleased_whileRecording_goesBackToTheRecordingMode() {
        stateHolder.update {
            it.copy(
                mode = CameraMode.VIDEO,
                recording = it.recording.copy(phase = RecordingPhase.STARTING),
            )
        }

        delegate.onStripReleased(CameraMode.CAMERA)

        assertEquals(ViewfinderModeStripState(selectionId = 1), state().modeStrip)
        assertEquals(CameraMode.VIDEO, state().modeStripTarget())
        assertFalse(state().preview.isLastFrameShown)
    }

    @Test
    fun sidewaysSwipes_moveAcrossTheModes() {
        delegate.onPreviewSwiped(SwipeDirection.LEFT)
        assertEquals(CameraMode.VIDEO, state().modeStripTarget())

        delegate.onPreviewSwiped(SwipeDirection.RIGHT)
        assertEquals(CameraMode.CAMERA, state().modeStripTarget())
    }

    @Test
    fun swipeUp_inQrMode_offersTheBarcodeFormats() {
        runTest {
            stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }
            val effects = collectEffects(stateHolder)

            delegate.onPreviewSwiped(SwipeDirection.UP)

            assertEquals(listOf(ViewfinderScreenEffect.ShowQrFormats), effects)
            assertEquals(ViewfinderModeStripState(), state().modeStrip)
        }
    }

    @Test
    fun aSecondFling_movesOnFromWhereTheFirstWasHeaded() {
        delegate.onPreviewSwiped(SwipeDirection.RIGHT)
        delegate.onPreviewSwiped(SwipeDirection.RIGHT)

        assertEquals(CameraMode.QR_SCAN, state().modeStripTarget())
        assertEquals(CameraMode.CAMERA, state().mode)
    }

    @Test
    fun aSwipePastTheLastMode_movesNothing() {
        stateHolder.update { it.copy(mode = CameraMode.VIDEO) }

        delegate.onPreviewSwiped(SwipeDirection.LEFT)

        assertEquals(ViewfinderModeStripState(), state().modeStrip)
    }

    @Test
    fun followCamera_putsTheStripOnTheCamerasModeWithNothingPending() {
        delegate.onStripReleased(CameraMode.VIDEO)
        stateHolder.update { it.copy(mode = CameraMode.CAMERA) }

        delegate.followCamera()

        assertEquals(
            ViewfinderModeStripState(selectionId = 2, isSwitchPending = true),
            state().modeStrip,
        )
        assertEquals(CameraMode.CAMERA, state().modeStripTarget())
    }

    @Test
    fun cancelSwitch_overALiveCamera_takesTheLastFrameDown() {
        delegate.onStripReleased(CameraMode.VIDEO)

        delegate.cancelSwitch()

        assertNull(state().modeStrip.pendingMode)
        assertFalse(state().modeStrip.isSwitchPending)
        assertFalse(state().preview.isLastFrameShown)
    }

    @Test
    fun cancelSwitch_whileTheCameraIsDown_leavesTheLastFrameUp() {
        delegate.onStripReleased(CameraMode.VIDEO)
        delegate.onPreviewStreaming(false)

        delegate.cancelSwitch()

        assertTrue(state().preview.isLastFrameShown)
    }

    @Test
    fun previewStreaming_takesTheLastFrameDownAndAStopPutsItUp() {
        delegate.onPreviewStreaming(false)
        assertEquals(ViewfinderPreviewState(isLastFrameShown = true), state().preview)

        delegate.onPreviewStreaming(true)
        assertEquals(ViewfinderPreviewState(isStreaming = true), state().preview)
    }

    @Test
    fun previewStreaming_endsThePendingSwitch() {
        delegate.onStripReleased(CameraMode.VIDEO)
        delegate.onPreviewStreaming(false)
        assertTrue(state().modeStrip.isSwitchPending)

        delegate.onPreviewStreaming(true)

        assertFalse(state().modeStrip.isSwitchPending)
    }

    private fun state(): ViewfinderState {
        return stateHolder.state.value
    }

    private companion object {
        val MODES = linkedSetOf(
            CameraMode.QR_SCAN,
            CameraMode.NIGHT,
            CameraMode.CAMERA,
            CameraMode.VIDEO,
        )
    }
}
