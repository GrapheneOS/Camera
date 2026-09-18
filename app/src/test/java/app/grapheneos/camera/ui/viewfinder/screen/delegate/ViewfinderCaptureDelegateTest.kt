package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderCaptureDelegateTest {

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = CameraMode.VIDEO, requiresVideoModeOnly = false),
        render = { ViewfinderUiState() },
    )

    @Test
    fun aPauseBeforeTheRecordingStarts_survivesTheStart() {
        val delegate = createDelegate()

        delegate.setRecordingPaused(paused = true)
        delegate.startRecording()

        assertEquals(
            ViewfinderCaptureState(isRecording = true, isRecordingPaused = true),
            capture(),
        )
    }

    @Test
    fun stopRecording_forgetsThePause() {
        val delegate = createDelegate()

        delegate.startRecording()
        delegate.setRecordingPaused(paused = true)
        delegate.stopRecording()

        assertEquals(ViewfinderCaptureState(), capture())
    }

    @Test
    fun pictureCapture_isInProgressUntilFinished() {
        val delegate = createDelegate()

        delegate.startPictureCapture()
        assertTrue(capture().isTakingPicture)

        delegate.finishPictureCapture()
        assertFalse(capture().isTakingPicture)
    }

    @Test
    fun pictureSave_isInProgressUntilFinished() {
        val delegate = createDelegate()

        delegate.startPictureSave()
        assertTrue(capture().isSavingPicture)

        delegate.finishPictureSave()
        assertFalse(capture().isSavingPicture)
    }

    @Test
    fun capturedPreview_isShownUntilDismissed() {
        val delegate = createDelegate()

        delegate.showCapturedPreview()
        assertTrue(capture().isCapturedPreviewShown)

        delegate.dismissCapturedPreview()
        assertFalse(capture().isCapturedPreviewShown)
    }

    @Test
    fun onScreenDestroyed_forgetsWhatTheScreenWasShowing() {
        val delegate = createDelegate()

        delegate.startRecording()
        delegate.showCapturedPreview()
        delegate.onScreenDestroyed()

        assertEquals(ViewfinderCaptureState(), capture())
    }

    private fun capture(): ViewfinderCaptureState {
        return stateHolder.state.value.capture
    }

    private fun createDelegate(): ViewfinderCaptureDelegate {
        val delegate = ViewfinderCaptureDelegateImpl()

        delegate.bind(stateHolder)

        return delegate
    }
}
