package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.capture.VideoRecorder
import app.grapheneos.camera.domain.capture.model.RecordVideoRequest
import app.grapheneos.camera.domain.capture.model.RecordedVideoEvent
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingState
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

interface ViewfinderRecordingDelegate {

    val recordingEvents: Flow<RecordedVideoEvent>

    fun bind(stateHolder: ViewfinderStateHolder)
    fun onScreenCreated(host: ViewfinderHost)
    fun onScreenDestroyed()

    fun requestRecording()
    fun prepareRecording(includeLocation: Boolean, includeAudio: Boolean)
    fun startPreparedRecording()

    fun startRecording()
    fun setRecordedDuration(duration: Duration)
    fun setPaused(paused: Boolean)
    fun setMuted(muted: Boolean)

    fun requestStop()
    fun markStopped()

    fun saveRecording()
    fun discardRecording()
}

internal class ViewfinderRecordingDelegateImpl @Inject constructor(
    private val videoRecorder: VideoRecorder,
    private val capturedItemRepository: CapturedItemRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderRecordingDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    private var host: ViewfinderHost? = null

    override val recordingEvents: Flow<RecordedVideoEvent> = videoRecorder.events

    override fun bind(stateHolder: ViewfinderStateHolder) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder
    }

    override fun onScreenCreated(host: ViewfinderHost) {
        this.host = host
    }

    override fun onScreenDestroyed() {
        host = null
        update { ViewfinderRecordingState() }
    }

    override fun requestRecording() {
        update { ViewfinderRecordingState(phase = RecordingPhase.STARTING) }
    }

    override fun prepareRecording(includeLocation: Boolean, includeAudio: Boolean) {
        applicationScope.launch(mainDispatcher) {
            videoRecorder.prepare(
                request = RecordVideoRequest(
                    storageLocation = capturedItemRepository.storageLocation.first(),
                    foreignUri = host?.chrome?.foreignOutputUri(),
                    includeLocation = includeLocation,
                    includeAudio = includeAudio,
                ),
                isStillWanted = { stateHolder.state.value.recording.isActive() },
            )
        }
    }

    override fun startPreparedRecording() {
        val recording = stateHolder.state.value.recording

        videoRecorder.start(
            muted = recording.isMuted,
            paused = recording.isPaused,
        )
    }

    override fun startRecording() {
        update { it.copy(phase = RecordingPhase.RECORDING) }
    }

    override fun setRecordedDuration(duration: Duration) {
        update { it.copy(duration = duration) }
    }

    // Not tied to the recording phase: the user may have paused before the recording actually
    // started.
    override fun setPaused(paused: Boolean) {
        update { it.copy(isPaused = paused) }
        videoRecorder.setPaused(paused)
    }

    override fun setMuted(muted: Boolean) {
        update { it.copy(isMuted = muted) }
        videoRecorder.setMuted(muted)
    }

    override fun requestStop() {
        videoRecorder.stop()
    }

    override fun markStopped() {
        update { ViewfinderRecordingState() }
    }

    override fun saveRecording() {
        videoRecorder.save()
    }

    override fun discardRecording() {
        videoRecorder.discard()
    }

    private fun update(transform: (ViewfinderRecordingState) -> ViewfinderRecordingState) {
        stateHolder.update { it.copy(recording = transform(it.recording)) }
    }
}
