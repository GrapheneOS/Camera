package app.grapheneos.camera.data.media.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import app.grapheneos.camera.data.media.model.VideoOutput
import app.grapheneos.camera.data.media.model.VideoOutputChange
import app.grapheneos.camera.data.media.model.VideoPlaybackState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface VideoPlayback {

    val state: StateFlow<VideoPlaybackState>

    val shownPosition: StateFlow<Duration>

    val currentPosition: Duration

    fun load(uri: Uri)

    fun unload()

    fun changeOutput(change: VideoOutputChange)

    fun play()

    fun pause()

    fun seekTo(position: Duration)

    fun setScrubbing(isScrubbing: Boolean)

    fun setMuted(isMuted: Boolean)

    fun setSpeed(speed: Float)

    fun setLooping(isLooping: Boolean)

    fun release()
}

@OptIn(UnstableApi::class)
internal class VideoPlaybackImpl(
    private val player: ExoPlayer,
) : VideoPlayback {

    private val _state = MutableStateFlow(VideoPlaybackState())
    override val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()

    private val _shownPosition = MutableStateFlow(Duration.ZERO)
    override val shownPosition: StateFlow<Duration> = _shownPosition.asStateFlow()

    override val currentPosition: Duration
        get() = player.currentPosition.milliseconds

    private var outputId: Int? = null

    private val frameListener = VideoFrameMetadataListener { presentationTimeUs, _, _, _ ->
        _shownPosition.value = presentationTimeUs.coerceAtLeast(0L).microseconds
    }

    private val listener = object : Player.Listener {

        override fun onTracksChanged(tracks: Tracks) {
            updateAudioFocus()
        }

        override fun onRenderedFirstFrame() {
            _state.value = _state.value.copy(
                hasRenderedFrame = true,
                renderedOutputId = outputId,
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "unable to play the video", error)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            _state.value = currentState()

            if (player.playbackState == Player.STATE_ENDED) {
                _shownPosition.value = player.duration.milliseconds
            }
        }
    }

    init {
        player.addListener(listener)
        player.setVideoFrameMetadataListener(frameListener)
        player.setAudioAttributes(MOVIE_AUDIO, false)
    }

    override fun load(uri: Uri) {
        releaseOutput()
        resetState()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
    }

    override fun unload() {
        releaseOutput()
        player.stop()
        player.clearMediaItems()
        resetState()
    }

    override fun changeOutput(change: VideoOutputChange) {
        when (change) {
            is VideoOutputChange.Attached -> attach(change.output)
            is VideoOutputChange.Detached -> detach(change.output)
        }
    }

    override fun play() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
        }
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(position: Duration) {
        player.seekTo(position.inWholeMilliseconds)
    }

    override fun setScrubbing(isScrubbing: Boolean) {
        player.isScrubbingModeEnabled = isScrubbing
    }

    override fun setMuted(isMuted: Boolean) {
        player.volume = when {
            isMuted -> 0f
            else -> 1f
        }
        updateAudioFocus()
    }

    override fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    override fun setLooping(isLooping: Boolean) {
        player.repeatMode = when {
            isLooping -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun release() {
        player.removeListener(listener)
        player.clearVideoFrameMetadataListener(frameListener)
        player.release()
    }

    private fun attach(output: VideoOutput) {
        outputId = output.id
        player.setVideoSurface(output.surface)

        // A paused player leaves a new surface black until a seek decodes the frame again.
        if (_state.value.hasRenderedFrame && !player.isPlaying) {
            player.seekTo(player.currentPosition)
        }
    }

    // Detaching a surface waits for the player to stop using it. Done before stop or setMediaItem it
    // is quick; done after them it waits until the codecs are released.
    private fun releaseOutput() {
        if (outputId == null) return

        outputId = null
        player.clearVideoSurface()
    }

    private fun detach(output: VideoOutput) {
        if (outputId != output.id) return

        outputId = null
        player.clearVideoSurface(output.surface)
    }

    private fun resetState() {
        _state.value = VideoPlaybackState()
        _shownPosition.value = Duration.ZERO
    }

    private fun updateAudioFocus() {
        val hasAudio = player.currentTracks.containsType(C.TRACK_TYPE_AUDIO)

        player.setAudioAttributes(MOVIE_AUDIO, hasAudio && player.volume > 0f)
    }

    private fun currentState(): VideoPlaybackState {
        return VideoPlaybackState(
            isPlaying = player.isPlaying,
            duration = player.duration.takeIf { it != C.TIME_UNSET }?.milliseconds,
            aspectRatio = aspectRatio(),
            hasRenderedFrame = _state.value.hasRenderedFrame,
            renderedOutputId = _state.value.renderedOutputId,
            hasFailed = player.playerError != null,
        )
    }

    private fun aspectRatio(): Float? {
        val size = player.videoSize

        return when {
            size.width <= 0 || size.height <= 0 -> null
            else -> size.width * size.pixelWidthHeightRatio / size.height
        }
    }

    private companion object {
        const val TAG = "VideoPlayback"

        val MOVIE_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }
}
