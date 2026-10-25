package app.grapheneos.camera.ui.mediaviewer.playback

import app.grapheneos.camera.data.media.model.VideoOutput
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
import kotlinx.coroutines.flow.map
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

    private val controls = MutableStateFlow(Controls())

    private val skipTaps = Channel<Unit>(capacity = Channel.CONFLATED)

    suspend fun track(followsPosition: Flow<Boolean>) {
        coroutineScope {
            launch {
                combine(playback.state, controls, ::uiModelOf).collect { uiModel ->
                    _state.value = uiModel
                }
            }

            launch {
                playback.shownPosition.collect(::land)
            }

            launch {
                controls
                    .map { it.playbackSpeed() }
                    .distinctUntilChanged()
                    .collect(playback::setSpeed)
            }

            launch {
                skipTaps.receiveAsFlow().collectLatest { _ ->
                    delay(SKIP_SHOWN_FOR)
                    controls.update { it.copy(skip = null) }
                }
            }

            val showsPosition = combine(
                followsPosition,
                controls.map { it.skip != null },
            ) { isFollowed, isSkipping -> isFollowed || isSkipping }

            showsPosition
                .distinctUntilChanged()
                .collectLatest { isShown ->
                    if (isShown) {
                        followPosition()
                    }
                }
        }
    }

    fun setOutput(output: VideoOutput?) {
        playback.setOutput(output)
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
        val dragging = (controls.value.seek as? Seek.Dragging)
            ?.copy(target = position)
            ?: startDragging(target = position)

        setSeek(dragging)
        playback.seekTo(position)
    }

    fun finishSeek() {
        val dragging = controls.value.seek as? Seek.Dragging ?: return

        playback.setScrubbing(false)
        playback.seekTo(dragging.target)

        when {
            dragging.resumesPlayback -> {
                setSeek(Seek.Landing(dragging.target))
                playback.play()
            }
            else -> setSeek(Seek.Held(dragging.target))
        }
    }

    fun skip(direction: SkipDirection) {
        val position = playback.currentPosition
        val target = (position + offsetOf(direction)).coerceIn(Duration.ZERO, end())
        val skipped = (target - position).absoluteValue

        if (skipped > Duration.ZERO) {
            setSeek(Seek.Landing(target))
            playback.seekTo(target)
        }

        controls.update {
            it.copy(
                skip = nextSkip(
                    shownSkip = it.skip,
                    direction = direction,
                    skipped = skipped,
                ),
            )
        }
        skipTaps.trySend(Unit)
    }

    fun setSpeed(speed: Float) {
        controls.update { it.copy(speed = speed) }
    }

    fun setLooping(isLooping: Boolean) {
        playback.setLooping(isLooping)
    }

    fun startFastForward() {
        controls.update { it.copy(isFastForwarding = true) }
        play()
    }

    fun stopFastForward() {
        controls.update { it.copy(isFastForwarding = false) }
    }

    private suspend fun followPosition() {
        val seek = controls.map { it.seek }.distinctUntilChanged()

        combine(
            playback.shownPosition,
            seek,
            ::positionOf,
        ).collect { position ->
            _position.value = position
        }
    }

    private fun land(shownPosition: Duration) {
        val landing = controls.value.seek as? Seek.Landing ?: return

        if (shownPosition - landing.target in Duration.ZERO..<LANDING_WINDOW) {
            setSeek(Seek.None)
        }
    }

    private fun setSeek(seek: Seek) {
        controls.update { it.copy(seek = seek) }
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
        setSeek(
            when (val seek = controls.value.seek) {
                is Seek.Held -> Seek.Landing(seek.target)
                else -> Seek.None
            },
        )
        playback.play()
    }

    private fun isPlaying(): Boolean {
        return (_state.value as? VideoPlaybackUiModel.Active)?.isPlaying == true
    }

    private fun uiModelOf(
        playbackState: VideoPlaybackState,
        controls: Controls,
    ): VideoPlaybackUiModel {
        return when {
            playbackState.hasFailed -> VideoPlaybackUiModel.Failed

            else -> VideoPlaybackUiModel.Active(
                isPlaying = playbackState.isPlaying,
                duration = playbackState.duration ?: Duration.ZERO,
                aspectRatio = playbackState.aspectRatio,
                hasRenderedFrame = playbackState.hasRenderedFrame,
                renderedOutputId = playbackState.renderedOutputId,
                isFastForwarding = controls.isFastForwarding,
                skip = controls.skip,
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
            is Seek.Landing -> seek.target
        }
    }

    private data class Controls(
        val seek: Seek = Seek.None,
        val speed: Float = NORMAL_SPEED,
        val isFastForwarding: Boolean = false,
        val skip: VideoSkipUiModel? = null,
    ) {

        fun playbackSpeed(): Float {
            return when {
                isFastForwarding -> FAST_FORWARD_SPEED
                else -> speed
            }
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

        data class Landing(
            val target: Duration,
        ) : Seek
    }

    private companion object {
        const val NORMAL_SPEED = 1f
        const val FAST_FORWARD_SPEED = 2f

        val SKIP_SHOWN_FOR = 500.milliseconds
        val LANDING_WINDOW = SkipDirection.STEP / 2
    }
}
