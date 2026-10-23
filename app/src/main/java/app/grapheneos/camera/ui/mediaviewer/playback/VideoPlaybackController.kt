package app.grapheneos.camera.ui.mediaviewer.playback

import android.view.Surface
import app.grapheneos.camera.data.media.model.VideoPlaybackState
import app.grapheneos.camera.data.media.player.VideoPlayback
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class VideoPlaybackController(
    private val playback: VideoPlayback,
) {

    private val _state = MutableStateFlow<VideoPlaybackUiModel>(VideoPlaybackUiModel.Active())
    val state: StateFlow<VideoPlaybackUiModel> = _state.asStateFlow()

    private val _position = MutableStateFlow(Duration.ZERO)
    val position: StateFlow<Duration> = _position.asStateFlow()

    private val seek = MutableStateFlow<Seek>(Seek.None)
    private val isFastForwarding = MutableStateFlow(false)
    private val skip = MutableStateFlow<VideoSkipUiModel?>(null)

    private val skipTaps = Channel<Unit>(capacity = Channel.CONFLATED)

    suspend fun track(followsPosition: Flow<Boolean>) {
        coroutineScope {
            launch {
                combine(
                    playback.state,
                    isFastForwarding,
                    skip,
                    ::uiModelOf,
                ).collect { uiModel ->
                    _state.value = uiModel
                }
            }

            launch {
                playback.shownPosition.collect(::landSkip)
            }

            launch {
                skipTaps.receiveAsFlow().collectLatest {
                    delay(SKIP_SHOWN_FOR)
                    skip.value = null
                }
            }

            combine(followsPosition, skip) { follows, shownSkip -> follows || shownSkip != null }
                .distinctUntilChanged()
                .collectLatest { follows ->
                    if (follows) {
                        followPosition()
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
        seek.value = when (val current = seek.value) {
            is Seek.Dragging -> current.copy(target = position)
            else -> startDragging(target = position)
        }
        playback.seekTo(position)
    }

    fun finishSeek() {
        val dragging = seek.value as? Seek.Dragging ?: return

        seek.value = Seek.Held(target = dragging.target)
        playback.setScrubbing(false)
        playback.seekTo(dragging.target)

        if (dragging.resumesPlayback) {
            play()
        }
    }

    fun skip(direction: SkipDirection) {
        val position = playback.currentPosition
        val target = (position + offsetOf(direction)).coerceIn(Duration.ZERO, end())
        val skipped = (target - position).absoluteValue

        if (skipped > Duration.ZERO) {
            seek.value = Seek.Skipping(target)
            playback.seekTo(target)
        }

        skip.update { shownSkip ->
            nextSkip(
                shownSkip = shownSkip,
                direction = direction,
                skipped = skipped,
            )
        }
        skipTaps.trySend(Unit)
    }

    fun startFastForward() {
        playback.setSpeed(FAST_FORWARD_SPEED)
        play()
        isFastForwarding.value = true
    }

    fun stopFastForward() {
        playback.setSpeed(NORMAL_SPEED)
        isFastForwarding.value = false
    }

    fun release() {
        playback.release()
    }

    private suspend fun followPosition() {
        combine(playback.shownPosition, seek, ::positionOf).collect { position ->
            _position.value = position
        }
    }

    private fun landSkip(shownPosition: Duration) {
        val skipping = seek.value as? Seek.Skipping ?: return

        if (shownPosition - skipping.target in Duration.ZERO..<SKIP_LANDING_WINDOW) {
            seek.value = Seek.None
        }
    }

    private fun nextSkip(
        shownSkip: VideoSkipUiModel?,
        direction: SkipDirection,
        skipped: Duration,
    ): VideoSkipUiModel {
        return when {
            shownSkip != null && shownSkip.direction == direction -> {
                shownSkip.copy(offset = shownSkip.offset + skipped)
            }
            else -> VideoSkipUiModel(
                direction = direction,
                offset = SkipDirection.STEP,
            )
        }
    }

    private fun offsetOf(direction: SkipDirection): Duration {
        return when (direction) {
            SkipDirection.Back -> -SkipDirection.STEP
            SkipDirection.Forward -> SkipDirection.STEP
        }
    }

    private fun end(): Duration {
        return playback.state.value.duration ?: Duration.INFINITE
    }

    private fun startDragging(target: Duration): Seek.Dragging {
        val resumesPlayback = isPlaying()

        playback.pause()
        playback.setScrubbing(true)

        return Seek.Dragging(
            target = target,
            resumesPlayback = resumesPlayback,
        )
    }

    private fun play() {
        seek.value = Seek.None
        playback.play()
    }

    private fun isPlaying(): Boolean {
        return (_state.value as? VideoPlaybackUiModel.Active)?.isPlaying == true
    }

    private fun uiModelOf(
        playbackState: VideoPlaybackState,
        isFastForwarding: Boolean,
        skip: VideoSkipUiModel?,
    ): VideoPlaybackUiModel {
        return when {
            playbackState.hasFailed -> VideoPlaybackUiModel.Failed

            else -> VideoPlaybackUiModel.Active(
                isPlaying = playbackState.isPlaying,
                duration = playbackState.duration ?: Duration.ZERO,
                aspectRatio = playbackState.aspectRatio,
                hasRenderedFrame = playbackState.hasRenderedFrame,
                isFastForwarding = isFastForwarding,
                skip = skip,
            )
        }
    }

    private fun positionOf(
        shownPosition: Duration,
        seek: Seek,
    ): Duration {
        return when (seek) {
            Seek.None -> shownPosition
            is Seek.Dragging -> seek.target
            is Seek.Held -> seek.target
            is Seek.Skipping -> seek.target
        }
    }

    private sealed interface Seek {

        data object None : Seek

        data class Dragging(
            val target: Duration,
            val resumesPlayback: Boolean,
        ) : Seek

        data class Held(
            val target: Duration,
        ) : Seek

        data class Skipping(
            val target: Duration,
        ) : Seek
    }

    private companion object {
        const val NORMAL_SPEED = 1f
        const val FAST_FORWARD_SPEED = 2f

        val SKIP_SHOWN_FOR = 500.milliseconds
        val SKIP_LANDING_WINDOW = SkipDirection.STEP / 2
    }
}
