package app.grapheneos.camera.ui.gallery.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.grapheneos.camera.ui.common.components.bottomBar
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryVideoUiModel
import app.grapheneos.camera.ui.mediaviewer.components.video.VideoControls
import app.grapheneos.camera.ui.mediaviewer.playback.VideoPlaybackUiModel
import kotlin.time.Duration

@Composable
internal fun GalleryVideoControls(
    video: GalleryVideoUiModel,
    position: () -> Duration,
    isVisible: Boolean,
    contentPadding: PaddingValues,
    fadeProgress: () -> Float,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = video.playback as? VideoPlaybackUiModel.Active

    AnimatedVisibility(
        visible = isVisible && playback != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        VideoControls(
            isPlaying = playback?.isPlaying == true,
            isMuted = video.isMuted,
            position = position(),
            duration = playback?.duration ?: Duration.ZERO,
            onPlayPauseClick = { onAction(Action.PlayPauseClicked) },
            onMuteClick = { onAction(Action.MuteClicked) },
            onSeek = { target -> onAction(Action.SeekDragged(target)) },
            onSeekFinished = { onAction(Action.SeekReleased) },
            modifier = Modifier
                .graphicsLayer { alpha = 1f - fadeProgress() }
                .padding(bottom = contentPadding.calculateBottomPadding())
                .consumeWindowInsets(WindowInsets.bottomBar),
        )
    }
}
