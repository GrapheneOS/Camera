package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingState
import javax.inject.Inject
import kotlin.time.Duration

interface ViewfinderRecordingDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)
    fun onScreenDestroyed()

    fun requestRecording()
    fun startRecording()
    fun setRecordedDuration(duration: Duration)
    fun setPaused(paused: Boolean)
    fun setMuted(muted: Boolean)
    fun stopRecording()
}

internal class ViewfinderRecordingDelegateImpl @Inject constructor() :
    ViewfinderRecordingDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    override fun bind(stateHolder: ViewfinderStateHolder) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder
    }

    override fun onScreenDestroyed() {
        update { ViewfinderRecordingState() }
    }

    override fun requestRecording() {
        update { ViewfinderRecordingState(phase = RecordingPhase.STARTING) }
    }

    override fun startRecording() {
        update { it.copy(phase = RecordingPhase.RECORDING) }
    }

    override fun setRecordedDuration(duration: Duration) {
        update { it.copy(duration = duration) }
    }

    override fun setPaused(paused: Boolean) {
        update { it.copy(isPaused = paused) }
    }

    override fun setMuted(muted: Boolean) {
        update { it.copy(isMuted = muted) }
    }

    override fun stopRecording() {
        update { ViewfinderRecordingState() }
    }

    private fun update(transform: (ViewfinderRecordingState) -> ViewfinderRecordingState) {
        stateHolder.update { it.copy(recording = transform(it.recording)) }
    }
}
