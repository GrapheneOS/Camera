package app.grapheneos.camera.ui.gallery.screen.delegate

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.VideoOutput
import app.grapheneos.camera.data.media.player.VideoPlayback
import app.grapheneos.camera.data.media.player.VideoPlaybackFactory
import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPlaybackOptions
import app.grapheneos.camera.ui.gallery.screen.model.GalleryVideoState
import app.grapheneos.camera.ui.mediaviewer.playback.SkipDirection
import app.grapheneos.camera.ui.mediaviewer.playback.VideoPlaybackController
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal interface GalleryPlaybackDelegate {

    val position: StateFlow<Duration>

    fun bind(stateHolder: GalleryStateHolder)

    suspend fun trackPlayback()

    fun setOutput(output: VideoOutput?)

    fun togglePlayback()

    fun toggleMute()

    fun setSpeed(speed: Float)

    fun toggleLooping()

    fun seekTo(position: Duration)

    fun finishSeek()

    fun skip(direction: SkipDirection)

    fun startFastForward()

    fun stopFastForward()

    fun pause()
}

internal class GalleryPlaybackDelegateImpl @Inject constructor(
    private val videoPlaybackFactory: VideoPlaybackFactory,
) : GalleryPlaybackDelegate {

    private val _position = MutableStateFlow(Duration.ZERO)
    override val position: StateFlow<Duration> = _position.asStateFlow()

    private lateinit var stateHolder: GalleryStateHolder

    private var playback: VideoPlayback? = null
    private var controller: VideoPlaybackController? = null

    override fun bind(stateHolder: GalleryStateHolder) {
        this.stateHolder = stateHolder
    }

    override suspend fun trackPlayback() {
        try {
            stateHolder.state
                .map { state -> state.settledVideo() }
                .distinctUntilChanged()
                .collectLatest { item ->
                    when (item) {
                        null -> playback?.unload()
                        else -> play(item)
                    }
                }
        } finally {
            playback?.release()
            playback = null
        }
    }

    override fun setOutput(output: VideoOutput?) {
        controller?.setOutput(output)
    }

    override fun togglePlayback() {
        controller?.togglePlayback()
    }

    override fun toggleMute() {
        updateOptions { it.copy(isMuted = !it.isMuted) }
    }

    override fun setSpeed(speed: Float) {
        updateOptions { it.copy(speed = speed) }
    }

    override fun toggleLooping() {
        updateOptions { it.copy(isLooping = !it.isLooping) }
    }

    override fun seekTo(position: Duration) {
        controller?.seekTo(position)
    }

    override fun finishSeek() {
        controller?.finishSeek()
    }

    override fun skip(direction: SkipDirection) {
        controller?.skip(direction)
    }

    override fun startFastForward() {
        controller?.startFastForward()
    }

    override fun stopFastForward() {
        controller?.stopFastForward()
    }

    override fun pause() {
        controller?.pause()
    }

    private suspend fun applyOptions(controller: VideoPlaybackController) {
        coroutineScope {
            launch {
                stateHolder.state
                    .map { state -> state.playbackOptions.isMuted || state.showsDetails }
                    .distinctUntilChanged()
                    .collect(controller::setMuted)
            }

            launch {
                stateHolder.state
                    .map { state -> state.playbackOptions.speed }
                    .distinctUntilChanged()
                    .collect(controller::setSpeed)
            }

            stateHolder.state
                .map { state -> state.playbackOptions.isLooping }
                .distinctUntilChanged()
                .collect(controller::setLooping)
        }
    }

    private suspend fun play(item: CapturedItem) {
        val playback = playback ?: videoPlaybackFactory.create().also { playback = it }
        val controller = VideoPlaybackController(playback)

        playback.load(item.uri)
        this.controller = controller

        try {
            coroutineScope {
                launch {
                    applyOptions(controller)
                }

                launch {
                    controller.state.collect { playback ->
                        stateHolder.update {
                            it.copy(
                                video = GalleryVideoState(
                                    item = item,
                                    playback = playback,
                                ),
                            )
                        }
                    }
                }

                launch {
                    controller.position.collect(_position)
                }

                controller.track(
                    followsPosition = stateHolder.state.map { state -> state.areControlsVisible },
                )
            }
        } finally {
            this.controller = null
            _position.value = Duration.ZERO
            stateHolder.update { it.copy(video = null) }
        }
    }

    private fun updateOptions(change: (GalleryPlaybackOptions) -> GalleryPlaybackOptions) {
        stateHolder.update { it.copy(playbackOptions = change(it.playbackOptions)) }
    }
}
