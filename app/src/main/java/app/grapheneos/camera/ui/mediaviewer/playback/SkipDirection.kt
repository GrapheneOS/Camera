package app.grapheneos.camera.ui.mediaviewer.playback

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal enum class SkipDirection {
    Back,
    Forward,
    ;

    companion object {
        val STEP: Duration = 5.seconds
    }
}
