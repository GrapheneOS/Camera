package app.grapheneos.camera.ui.videoplayer.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.ui.core.CameraPreviewTheme
import app.grapheneos.camera.ui.core.CollectEvents
import app.grapheneos.camera.ui.core.cameraColors
import app.grapheneos.camera.ui.mediaviewer.components.SystemBarsVisibilityEffect
import app.grapheneos.camera.ui.mediaviewer.components.ViewerTopBar
import app.grapheneos.camera.ui.mediaviewer.components.video.VideoControls
import app.grapheneos.camera.ui.mediaviewer.components.video.VideoSurface
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerAction as Action
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerUiState as UiState
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun VideoPlayerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    screenModel: VideoPlayerScreenModel = hiltViewModel<VideoPlayerViewModel>(),
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()
    val effectHandler = rememberVideoPlayerEffectHandler()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        screenModel.onAction(Action.ScreenStopped)
    }

    CollectEvents(
        events = screenModel.effects,
        onEvent = effectHandler::handle,
    )

    SystemBarsVisibilityEffect(visible = uiState.areControlsVisible)

    VideoPlayerContent(
        uiState = uiState,
        onAction = screenModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
private fun VideoPlayerContent(
    uiState: UiState,
    onAction: (Action) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.cameraColors.onOverlay)
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = { onAction(Action.ScreenTapped) },
            )
            .then(
                when {
                    uiState.isPlaying -> Modifier.keepScreenOn()
                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        VideoSurface(
            aspectRatio = uiState.aspectRatio,
            onSurfaceCreated = { surface -> onAction(Action.SurfaceCreated(surface)) },
            onSurfaceDestroyed = { onAction(Action.SurfaceDestroyed) },
        )

        AnimatedVisibility(
            visible = uiState.areControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ViewerTopBar(onNavigateBack = onNavigateBack)
                Spacer(modifier = Modifier.weight(1f))
                VideoControls(
                    isPlaying = uiState.isPlaying,
                    position = uiState.position,
                    duration = uiState.duration,
                    onPlayPauseClick = { onAction(Action.PlayPauseClicked) },
                    onSeek = { position -> onAction(Action.SeekDragged(position)) },
                    onSeekFinished = { onAction(Action.SeekReleased) },
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun VideoPlayerContentPreview() {
    CameraPreviewTheme {
        VideoPlayerContent(
            uiState = UiState(
                isPlaying = true,
                position = 5.seconds,
                duration = 12.seconds,
                aspectRatio = 16f / 9f,
            ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}
