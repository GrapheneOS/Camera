package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import javax.inject.Inject

interface ViewfinderCaptureDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)
    fun onScreenDestroyed()

    fun startRecording()
    fun setRecordingPaused(paused: Boolean)
    fun stopRecording()

    fun startPictureCapture()
    fun finishPictureCapture()

    fun startPictureSave()
    fun finishPictureSave()

    fun showCapturedPreview()
    fun dismissCapturedPreview()
}

internal class ViewfinderCaptureDelegateImpl @Inject constructor() : ViewfinderCaptureDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    override fun bind(stateHolder: ViewfinderStateHolder) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder
    }

    override fun onScreenDestroyed() {
        stateHolder.update { it.copy(capture = ViewfinderCaptureState()) }
    }

    override fun startRecording() {
        updateCapture { it.copy(isRecording = true) }
    }

    // Not tied to isRecording: the user may have paused before the recording actually started.
    override fun setRecordingPaused(paused: Boolean) {
        updateCapture { it.copy(isRecordingPaused = paused) }
    }

    override fun stopRecording() {
        updateCapture {
            it.copy(
                isRecording = false,
                isRecordingPaused = false,
            )
        }
    }

    override fun startPictureCapture() {
        updateCapture { it.copy(isTakingPicture = true) }
    }

    override fun finishPictureCapture() {
        updateCapture { it.copy(isTakingPicture = false) }
    }

    override fun startPictureSave() {
        updateCapture { it.copy(isSavingPicture = true) }
    }

    override fun finishPictureSave() {
        updateCapture { it.copy(isSavingPicture = false) }
    }

    override fun showCapturedPreview() {
        updateCapture { it.copy(isCapturedPreviewShown = true) }
    }

    override fun dismissCapturedPreview() {
        updateCapture { it.copy(isCapturedPreviewShown = false) }
    }

    private fun updateCapture(transform: (ViewfinderCaptureState) -> ViewfinderCaptureState) {
        stateHolder.update { it.copy(capture = transform(it.capture)) }
    }
}
