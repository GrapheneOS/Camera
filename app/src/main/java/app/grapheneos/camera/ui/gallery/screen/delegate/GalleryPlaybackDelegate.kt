package app.grapheneos.camera.ui.gallery.screen.delegate

import android.view.Surface
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.player.VideoPlaybackFactory
import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
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

    fun setSurface(surface: Surface?)

    fun togglePlayback()

    fun toggleMute()

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

    private var controller: VideoPlaybackController? = null

    override fun bind(stateHolder: GalleryStateHolder) {
        this.stateHolder = stateHolder
    }

    override suspend fun trackPlayback() {
        stateHolder.state
            .map { state -> state.settledVideo() }
            .distinctUntilChanged()
            .collectLatest { item ->
                if (item != null) {
                    play(item)
                }
            }
    }

    override fun setSurface(surface: Surface?) {
        controller?.setSurface(surface)
    }

    override fun togglePlayback() {
        controller?.togglePlayback()
    }

    override fun toggleMute() {
        stateHolder.update { it.copy(isMuted = !it.isMuted) }
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

    private suspend fun play(item: CapturedItem) {
        val controller = VideoPlaybackController(videoPlaybackFactory.create(item.uri))
        this.controller = controller

        try {
            coroutineScope {
                launch {
                    stateHolder.state
                        .map { state -> state.isMuted || state.showsDetails }
                        .distinctUntilChanged()
                        .collect(controller::setMuted)
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
            controller.release()
            _position.value = Duration.ZERO
            stateHolder.update { it.copy(video = null) }
        }
    }
}
