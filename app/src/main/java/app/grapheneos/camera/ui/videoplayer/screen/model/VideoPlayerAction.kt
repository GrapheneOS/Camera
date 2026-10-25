package app.grapheneos.camera.ui.videoplayer.screen.model

import app.grapheneos.camera.data.media.model.VideoOutput
import kotlin.time.Duration

internal sealed interface VideoPlayerAction {

    data object ScreenTapped : VideoPlayerAction

    data object PlayPauseClicked : VideoPlayerAction

    data object MuteClicked : VideoPlayerAction

    data object SeekReleased : VideoPlayerAction

    data object ScreenStopped : VideoPlayerAction

    data class SeekDragged(
        val position: Duration,
    ) : VideoPlayerAction

    data class OutputChanged(
        val output: VideoOutput?,
    ) : VideoPlayerAction
}
