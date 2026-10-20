package app.grapheneos.camera.ui.gallery.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.ui.core.effect.CollectEvents
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.core.preview.PREVIEW_COOL_ICON
import app.grapheneos.camera.ui.core.preview.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.preview.previewShot
import app.grapheneos.camera.ui.core.theme.cameraColors
import app.grapheneos.camera.ui.gallery.components.GalleryActionBar
import app.grapheneos.camera.ui.gallery.components.GalleryPager
import app.grapheneos.camera.ui.gallery.components.gesture.dismissGestureInput
import app.grapheneos.camera.ui.gallery.components.gesture.rememberDismissGestureState
import app.grapheneos.camera.ui.gallery.components.rememberDismissTransform
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryUiState as UiState
import app.grapheneos.camera.ui.mediaviewer.components.SystemBarsVisibilityEffect
import app.grapheneos.camera.ui.mediaviewer.components.animateViewerBackgroundAsState
import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncher
import app.grapheneos.camera.ui.mediaviewer.navigation.VideoPlayerLauncher
import java.time.LocalDateTime
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun GalleryScreen(
    videoPlayerLauncher: VideoPlayerLauncher,
    systemGalleryLauncher: SystemGalleryLauncher,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    screenModel: GalleryScreenModel = hiltViewModel<GalleryViewModel>(),
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<GalleryMessage?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val effectHandler = rememberGalleryEffectHandler(
        videoPlayerLauncher = videoPlayerLauncher,
        systemGalleryLauncher = systemGalleryLauncher,
        onShowMessage = { text -> message = GalleryMessage(text) },
    )

    CollectEvents(
        events = screenModel.effects,
        onEvent = effectHandler::handle,
    )

    GalleryMessageEffect(
        message = message,
        snackbarHostState = snackbarHostState,
    )

    SystemBarsVisibilityEffect(
        visible = uiState.areControlsVisible,
    )

    GalleryContent(
        uiState = uiState,
        onAction = screenModel::onAction,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
private fun GalleryContent(
    uiState: UiState,
    onAction: (Action) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val dismissedBackground = MaterialTheme.cameraColors.onOverlay
    val dismissGestureState = rememberDismissGestureState()
    val background = animateViewerBackgroundAsState(uiState.areControlsVisible)
    val dismissTransform = rememberDismissTransform(
        dismissGestureState = dismissGestureState,
        onDismissed = onNavigateBack,
    )
    val backgroundColor = remember(background, dismissedBackground, dismissTransform) {
        {
            lerp(
                start = background.value,
                stop = dismissedBackground,
                fraction = dismissTransform.progress(),
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(backgroundColor()) },
    ) {
        GalleryPager(
            pages = uiState.pages,
            initialPage = uiState.currentPage,
            isZoomed = uiState.isZoomed,
            onPageSelected = { page -> onAction(Action.PageSelected(page)) },
            onTap = { onAction(Action.ScreenTapped) },
            onVideoClick = { onAction(Action.VideoClicked) },
            onZoomChanged = { pageKey, isZoomed ->
                onAction(
                    Action.ZoomChanged(
                        pageKey = pageKey,
                        isZoomed = isZoomed,
                    ),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .dismissGestureInput(
                    dismissGestureState = dismissGestureState,
                    isZoomed = uiState.isZoomed,
                )
                .graphicsLayer(dismissTransform::applyTo),
        )

        GalleryBars(
            uiState = uiState,
            areVisible = uiState.areControlsVisible,
            dismissProgress = dismissTransform::progress,
            backgroundColor = backgroundColor,
            onAction = onAction,
            onNavigateBack = onNavigateBack,
            snackbarHostState = snackbarHostState,
        )
    }

    GalleryDialogs(
        dialog = uiState.dialog,
        onAction = onAction,
    )
}

@Composable
private fun BoxScope.GalleryBars(
    uiState: UiState,
    areVisible: Boolean,
    dismissProgress: () -> Float,
    backgroundColor: () -> Color,
    onAction: (Action) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val exit = when {
        uiState.isZoomed -> ExitTransition.None
        else -> fadeOut()
    }

    AnimatedVisibility(
        visible = areVisible,
        enter = fadeIn(),
        exit = exit,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer { alpha = 1f - dismissProgress() },
    ) {
        GalleryTopBar(
            uiState = uiState,
            onAction = onAction,
            onNavigateBack = onNavigateBack,
            modifier = Modifier.drawBehind { drawRect(backgroundColor()) },
            containerColor = Color.Transparent,
        )
    }

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .graphicsLayer { alpha = 1f - dismissProgress() },
    ) {
        SnackbarHost(hostState = snackbarHostState)
        AnimatedVisibility(
            visible = areVisible && uiState.pages.isNotEmpty(),
            enter = fadeIn(),
            exit = exit,
        ) {
            GalleryActionBar(
                showsExternalActions = uiState.showsExternalActions,
                onShareClick = { onAction(Action.ShareClicked) },
                onEditClick = { onAction(Action.EditClicked) },
                onDeleteClick = { onAction(Action.DeleteClicked) },
                modifier = Modifier.drawBehind { drawRect(backgroundColor()) },
                containerColor = Color.Transparent,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun GalleryContentPreview() {
    CameraPreviewTheme {
        GalleryContent(
            uiState = UiState(
                pages = persistentListOf(
                    GalleryPageUiModel(
                        key = "content://media/external/images/media/1",
                        isVideo = false,
                        preview = GalleryPreview.Loaded(
                            previewShot(
                                top = PREVIEW_COOL_ICON,
                                bottom = PREVIEW_SCENE,
                            ),
                        ),
                    ),
                ),
                capturedAt = LocalDateTime.of(2023, 10, 16, 12, 0),
            ),
            onAction = {},
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
