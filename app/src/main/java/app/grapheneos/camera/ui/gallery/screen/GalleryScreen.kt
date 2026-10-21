package app.grapheneos.camera.ui.gallery.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import app.grapheneos.camera.ui.gallery.components.GalleryPager
import app.grapheneos.camera.ui.gallery.components.gesture.ViewerTransformState
import app.grapheneos.camera.ui.gallery.components.gesture.rememberViewerTransformState
import app.grapheneos.camera.ui.gallery.components.rememberViewerTransform
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
    val currentPageKey = uiState.pages.getOrNull(uiState.currentPage)?.key
    val transformState = rememberViewerTransformState(pageKey = currentPageKey)
    val viewerTransform = rememberViewerTransform(
        transformState = transformState,
        onDismissed = onNavigateBack,
    )
    val background = animateViewerBackgroundAsState(
        areControlsVisible = uiState.areControlsVisible && !uiState.isZoomed,
    )
    val backgroundColor = remember(background, dismissedBackground, viewerTransform) {
        {
            lerp(
                start = background.value,
                stop = dismissedBackground,
                fraction = viewerTransform.fadeProgress(),
            )
        }
    }

    ZoomEffect(
        pageKey = currentPageKey,
        transformState = transformState,
        onAction = onAction,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(backgroundColor()) },
    ) {
        GalleryPager(
            pages = uiState.pages,
            initialPage = uiState.currentPage,
            currentPage = uiState.currentPage,
            transformState = transformState,
            viewerTransform = viewerTransform,
            onPageSelected = { page -> onAction(Action.PageSelected(page)) },
            onTap = { onAction(Action.ScreenTapped) },
            onVideoClick = { onAction(Action.VideoClicked) },
            modifier = Modifier.fillMaxSize(),
        )

        GalleryBars(
            uiState = uiState,
            fadeProgress = viewerTransform::fadeProgress,
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
private fun ZoomEffect(
    pageKey: String?,
    transformState: ViewerTransformState,
    onAction: (Action) -> Unit,
) {
    val currentOnAction by rememberUpdatedState(onAction)

    LaunchedEffect(pageKey, transformState) {
        if (pageKey == null) return@LaunchedEffect

        snapshotFlow { transformState.isZoomed() }
            .collect { isZoomed ->
                currentOnAction(
                    Action.ZoomChanged(
                        pageKey = pageKey,
                        isZoomed = isZoomed,
                    ),
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
