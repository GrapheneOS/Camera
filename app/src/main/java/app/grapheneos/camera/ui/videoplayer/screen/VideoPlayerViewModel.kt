package app.grapheneos.camera.ui.videoplayer.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.media.player.VideoPlaybackFactory
import app.grapheneos.camera.ui.mediaviewer.playback.VideoPlaybackController
import app.grapheneos.camera.ui.mediaviewer.playback.VideoPlaybackUiModel
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerAction as Action
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerScreenEffect as Effect
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val arguments: SavedStateHandle,
    videoPlaybackFactory: VideoPlaybackFactory,
) : ViewModel(),
    VideoPlayerScreenModel {

    private val _uiState = MutableStateFlow(VideoPlayerUiState(isMuted = isMutedOf(arguments)))
    override val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)
    override val effects: Flow<Effect> = _effects.receiveAsFlow()

    private val controller = VideoPlaybackController(
        videoPlaybackFactory.create(videoUriOf(arguments)),
    )

    init {
        controller.setMuted(_uiState.value.isMuted)

        viewModelScope.launch {
            controller.state.collect { video ->
                _uiState.update { it.copy(video = video) }
            }
        }

        viewModelScope.launch {
            controller.position.collect { position ->
                _uiState.update { it.copy(position = position) }
            }
        }

        viewModelScope.launch {
            controller.track(followsPosition = _uiState.map { it.areControlsVisible })
        }

        viewModelScope.launch {
            controller.state.first { it is VideoPlaybackUiModel.Failed }
            _effects.send(Effect.PlaybackFailed)
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.SurfaceCreated -> controller.setSurface(action.surface)
            is Action.SurfaceDestroyed -> controller.setSurface(null)
            is Action.ScreenTapped -> toggleControls()
            is Action.PlayPauseClicked -> controller.togglePlayback()
            is Action.MuteClicked -> toggleMute()
            is Action.SeekDragged -> controller.seekTo(action.position)
            is Action.SeekReleased -> controller.finishSeek()
            is Action.ScreenStopped -> controller.pause()
        }
    }

    override fun onCleared() {
        controller.release()
    }

    private fun toggleMute() {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
        controller.setMuted(_uiState.value.isMuted)
        saveIsMuted(arguments, _uiState.value.isMuted)
    }

    private fun toggleControls() {
        _uiState.update { it.copy(areControlsVisible = !it.areControlsVisible) }
    }
}
