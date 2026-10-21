package app.grapheneos.camera.ui.videoplayer.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.media.player.VideoPlaybackFactory
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerAction as Action
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerScreenEffect as Effect
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal interface VideoPlayerScreenModel {

    val uiState: StateFlow<VideoPlayerUiState>

    val effects: Flow<Effect>

    fun onAction(action: Action)
}

@HiltViewModel
internal class VideoPlayerViewModel @Inject constructor(
    arguments: SavedStateHandle,
    videoPlaybackFactory: VideoPlaybackFactory,
) : ViewModel(),
    VideoPlayerScreenModel {

    private val playback = videoPlaybackFactory.create(videoUriOf(arguments))

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    override val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)
    override val effects: Flow<Effect> = _effects.receiveAsFlow()

    private var isSeeking = false
    private var resumesAfterSeek = false
    private var holdsSeekPosition = false

    init {
        viewModelScope.launch {
            playback.state.collect { state ->
                _uiState.update {
                    it.copy(
                        isPlaying = state.isPlaying,
                        duration = state.duration ?: Duration.ZERO,
                        aspectRatio = state.aspectRatio,
                    )
                }
            }
        }

        viewModelScope.launch {
            _uiState
                .map { it.areControlsVisible }
                .distinctUntilChanged()
                .collectLatest { areControlsVisible ->
                    if (areControlsVisible) {
                        followShownPosition()
                    }
                }
        }

        viewModelScope.launch {
            playback.state.first { it.hasFailed }
            _effects.send(Effect.PlaybackFailed)
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.SurfaceCreated -> playback.setSurface(action.surface)
            is Action.SurfaceDestroyed -> playback.setSurface(null)
            is Action.ScreenTapped -> toggleControls()
            is Action.PlayPauseClicked -> togglePlayback()
            is Action.SeekDragged -> seekTo(action.position)
            is Action.SeekReleased -> finishSeek()
            is Action.ScreenStopped -> playback.pause()
        }
    }

    override fun onCleared() {
        playback.release()
    }

    private suspend fun followShownPosition() {
        playback.shownPosition.collect { position ->
            if (!holdsSeekPosition) {
                _uiState.update { it.copy(position = position) }
            }
        }
    }

    private fun toggleControls() {
        _uiState.update { it.copy(areControlsVisible = !it.areControlsVisible) }
    }

    private fun togglePlayback() {
        when {
            _uiState.value.isPlaying -> playback.pause()
            else -> play()
        }
    }

    private fun play() {
        holdsSeekPosition = false
        playback.play()
    }

    private fun seekTo(position: Duration) {
        if (!isSeeking) {
            isSeeking = true
            holdsSeekPosition = true
            resumesAfterSeek = _uiState.value.isPlaying
            playback.pause()
            playback.setScrubbing(true)
        }

        playback.seekTo(position)
        _uiState.update { it.copy(position = position) }
    }

    private fun finishSeek() {
        isSeeking = false
        playback.setScrubbing(false)
        playback.seekTo(_uiState.value.position)

        if (resumesAfterSeek) {
            play()
        }
    }
}
