package app.grapheneos.camera.ui.mediaviewer.playback

import android.view.Surface
import app.grapheneos.camera.data.media.model.VideoPlaybackState
import app.grapheneos.camera.data.media.player.VideoPlayback
import kotlin.time.Duration
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal class VideoPlaybackController(
    private val playback: VideoPlayback,
) {

    private val _state = MutableStateFlow<VideoPlaybackUiModel>(VideoPlaybackUiModel.Active())
    val state: StateFlow<VideoPlaybackUiModel> = _state.asStateFlow()

    private val _position = MutableStateFlow(Duration.ZERO)
    val position: StateFlow<Duration> = _position.asStateFlow()

    private var seek: Seek = Seek.None

    suspend fun track(followsPosition: Flow<Boolean>) {
        coroutineScope {
            launch {
                playback.state.collect { playbackState ->
                    _state.value = uiModelOf(playbackState)
                }
            }

            followsPosition
                .distinctUntilChanged()
                .collectLatest { follows ->
                    if (follows) {
                        followShownPosition()
                    }
                }
        }
    }

    fun setSurface(surface: Surface?) {
        playback.setSurface(surface)
    }

    fun togglePlayback() {
        when {
            isPlaying() -> playback.pause()
            else -> play()
        }
    }

    fun pause() {
        playback.pause()
    }

    fun setMuted(isMuted: Boolean) {
        playback.setMuted(isMuted)
    }

    fun seekTo(position: Duration) {
        if (seek !is Seek.Dragging) {
            seek = Seek.Dragging(resumesPlayback = isPlaying())
            playback.pause()
            playback.setScrubbing(true)
        }

        playback.seekTo(position)
        _position.value = position
    }

    fun finishSeek() {
        val dragging = seek as? Seek.Dragging ?: return

        seek = Seek.Held
        playback.setScrubbing(false)
        playback.seekTo(_position.value)

        if (dragging.resumesPlayback) {
            play()
        }
    }

    fun release() {
        playback.release()
    }

    private suspend fun followShownPosition() {
        playback.shownPosition.collect { position ->
            if (seek == Seek.None) {
                _position.value = position
            }
        }
    }

    private fun play() {
        seek = Seek.None
        playback.play()
    }

    private fun isPlaying(): Boolean {
        return (_state.value as? VideoPlaybackUiModel.Active)?.isPlaying == true
    }

    private fun uiModelOf(playbackState: VideoPlaybackState): VideoPlaybackUiModel {
        return when {
            playbackState.hasFailed -> VideoPlaybackUiModel.Failed
            else -> VideoPlaybackUiModel.Active(
                isPlaying = playbackState.isPlaying,
                duration = playbackState.duration ?: Duration.ZERO,
                aspectRatio = playbackState.aspectRatio,
                hasRenderedFrame = playbackState.hasRenderedFrame,
            )
        }
    }

    private sealed interface Seek {

        data object None : Seek

        data class Dragging(
            val resumesPlayback: Boolean,
        ) : Seek

        data object Held : Seek
    }
}
