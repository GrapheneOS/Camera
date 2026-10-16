package app.grapheneos.camera.ui.videoplayer.screen.model

import android.view.Surface
import kotlin.time.Duration

internal sealed interface VideoPlayerAction {

    data class SurfaceCreated(
        val surface: Surface,
    ) : VideoPlayerAction

    data object SurfaceDestroyed : VideoPlayerAction

    data object ScreenTapped : VideoPlayerAction

    data object PlayPauseClicked : VideoPlayerAction

    data class SeekDragged(
        val position: Duration,
    ) : VideoPlayerAction

    data object SeekReleased : VideoPlayerAction

    data object ScreenStopped : VideoPlayerAction
}
