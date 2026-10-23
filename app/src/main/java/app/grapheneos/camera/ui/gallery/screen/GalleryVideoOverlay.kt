package app.grapheneos.camera.ui.gallery.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.common.components.topBar
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryVideoUiModel
import app.grapheneos.camera.ui.mediaviewer.components.video.FastForwardBadge
import app.grapheneos.camera.ui.mediaviewer.components.video.VideoControls
import app.grapheneos.camera.ui.mediaviewer.playback.VideoPlaybackUiModel
import kotlin.time.Duration

private val BADGE_TOP_MARGIN = 16.dp

@Composable
internal fun GalleryVideoOverlay(
    video: GalleryVideoUiModel,
    position: () -> Duration,
    areControlsVisible: Boolean,
    contentPadding: PaddingValues,
    fadeProgress: () -> Float,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = video.playback as? VideoPlaybackUiModel.Active ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - fadeProgress() },
    ) {
        FastForwardIndicator(
            isVisible = playback.isFastForwarding,
            topPadding = PaddingValues(top = contentPadding.calculateTopPadding()),
            modifier = Modifier.align(Alignment.TopCenter),
        )
        PlaybackControls(
            playback = playback,
            isMuted = video.isMuted,
            position = position,
            isVisible = areControlsVisible,
            bottomPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            onAction = onAction,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun FastForwardIndicator(
    isVisible: Boolean,
    topPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .padding(topPadding)
            .consumeWindowInsets(topPadding)
            .windowInsetsPadding(WindowInsets.topBar)
            .padding(top = BADGE_TOP_MARGIN),
    ) {
        FastForwardBadge()
    }
}

@Composable
private fun PlaybackControls(
    playback: VideoPlaybackUiModel.Active,
    isMuted: Boolean,
    position: () -> Duration,
    isVisible: Boolean,
    bottomPadding: PaddingValues,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .padding(bottomPadding)
            .consumeWindowInsets(bottomPadding),
    ) {
        VideoControls(
            isPlaying = playback.isPlaying,
            isMuted = isMuted,
            position = position(),
            duration = playback.duration,
            onPlayPauseClick = { onAction(Action.PlayPauseClicked) },
            onMuteClick = { onAction(Action.MuteClicked) },
            onSeek = { target -> onAction(Action.SeekDragged(target)) },
            onSeekFinished = { onAction(Action.SeekReleased) },
        )
    }
}
