package app.grapheneos.camera.ui.videoplayer.screen.model

internal sealed interface VideoPlayerScreenEffect {

    data object PlaybackFailed : VideoPlayerScreenEffect
}
