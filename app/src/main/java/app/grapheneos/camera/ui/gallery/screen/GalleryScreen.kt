package app.grapheneos.camera.ui.gallery.screen

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.effect.CollectEvents
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.core.preview.PREVIEW_COOL_ICON
import app.grapheneos.camera.ui.core.preview.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.preview.previewShot
import app.grapheneos.camera.ui.core.theme.cameraColors
import app.grapheneos.camera.ui.gallery.components.GalleryPager
import app.grapheneos.camera.ui.gallery.components.cameraPageProgress
import app.grapheneos.camera.ui.gallery.components.GalleryPlaceholder
import app.grapheneos.camera.ui.gallery.components.ViewerTransform
import app.grapheneos.camera.ui.gallery.components.gesture.DetailsPanelState
import app.grapheneos.camera.ui.gallery.components.gesture.ViewerTransformState
import app.grapheneos.camera.ui.gallery.components.gesture.animateDetailsPositionAsState
import app.grapheneos.camera.ui.gallery.components.gesture.rememberDetailsPanelState
import app.grapheneos.camera.ui.gallery.components.gesture.rememberViewerTransformState
import app.grapheneos.camera.ui.gallery.components.rememberGalleryPagerState
import app.grapheneos.camera.ui.gallery.components.rememberViewerTransform
import app.grapheneos.camera.ui.gallery.components.settledItemIndex
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryMessage
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryUiState as UiState
import app.grapheneos.camera.ui.mediaviewer.components.HdrColorModeEffect
import app.grapheneos.camera.ui.mediaviewer.components.SystemBarsVisibilityEffect
import app.grapheneos.camera.ui.mediaviewer.components.animateViewerBackgroundAsState
import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncher
import app.grapheneos.camera.ui.mediaviewer.navigation.VideoPlayerLauncher
import java.time.LocalDateTime
import kotlin.math.max
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

private const val PREVIEW_PAGE_KEY = "content://media/external/images/media/1"

@Composable
internal fun GalleryScreen(
    videoPlayerLauncher: VideoPlayerLauncher,
    systemGalleryLauncher: SystemGalleryLauncher,
    clipboardManager: ClipboardManager,
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
        clipboardManager = clipboardManager,
        onShowMessage = { shownMessage -> message = shownMessage },
    )

    CollectEvents(
        events = screenModel.effects,
        onEvent = effectHandler::handle,
    )

    GalleryMessageEffect(
        message = message,
        snackbarHostState = snackbarHostState,
        onUndoClick = { screenModel.onAction(Action.UndoClicked) },
        onUndoDismiss = { screenModel.onAction(Action.UndoDismissed) },
    )

    SystemBarsVisibilityEffect(
        visible = uiState.areControlsVisible || uiState.showsDetails,
    )

    HdrColorModeEffect(
        isHdr = uiState.showsHdr,
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
    val pagerState = rememberGalleryPagerState(
        currentPage = uiState.currentPage,
        itemCount = { uiState.pages.size },
    )
    val settledPageKey = uiState.pages.getOrNull(pagerState.settledItemIndex)?.key
    val transformState = rememberViewerTransformState(pageKey = settledPageKey)
    val detailsState = rememberDetailsPanelState(isShown = uiState.showsDetails)
    val detailsPosition = animateDetailsPositionAsState(detailsState)
    val viewerTransform = rememberViewerTransform(
        transformState = transformState,
        onDismissed = onNavigateBack,
    )
    val backgroundColor = rememberBackgroundColor(
        uiState = uiState,
        fadeProgress = viewerTransform::fadeProgress,
    )

    GalleryViewerEffects(
        pageKey = settledPageKey,
        transformState = transformState,
        detailsState = detailsState,
        showsDetails = uiState.showsDetails,
        onAction = onAction,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(backgroundColor()) },
    ) {
        GalleryViewerPager(
            uiState = uiState,
            pagerState = pagerState,
            transformState = transformState,
            viewerTransform = viewerTransform,
            detailsState = detailsState,
            detailsPosition = { detailsPosition.value },
            onAction = onAction,
            onNavigateBack = onNavigateBack,
        )

        if (uiState.isEmpty) {
            EmptyGalleryPlaceholder(
                onOpenCameraClick = onNavigateBack,
            )
        }

        GalleryBars(
            uiState = uiState,
            fadeProgress = { max(viewerTransform.fadeProgress(), pagerState.cameraPageProgress) },
            detailsPosition = { detailsPosition.value },
            backButtonVisibility = { detailsState.backButtonVisibilityAt(detailsPosition.value) },
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
private fun GalleryViewerPager(
    uiState: UiState,
    pagerState: PagerState,
    transformState: ViewerTransformState,
    viewerTransform: ViewerTransform,
    detailsState: DetailsPanelState,
    detailsPosition: () -> Float,
    onAction: (Action) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val openMap = { onAction(Action.OpenMapClicked) }

    GalleryPager(
        pages = uiState.pages,
        previews = uiState.previews,
        details = uiState.details,
        pagerState = pagerState,
        currentPage = uiState.currentPage,
        showsDetails = uiState.showsDetails,
        transformState = transformState,
        viewerTransform = viewerTransform,
        detailsState = detailsState,
        detailsPosition = detailsPosition,
        onPageSelected = { page -> onAction(Action.PageSelected(page)) },
        onDetailsShownChange = { isShown -> onAction(Action.DetailsShownChanged(isShown)) },
        onTap = { onAction(Action.ScreenTapped) },
        onVideoClick = { onAction(Action.VideoClicked) },
        onOpenMapClick = openMap.takeIf { uiState.showsExternalActions },
        onCopy = { text -> onAction(Action.DetailLongPressed(text)) },
        onCameraPageReached = onNavigateBack,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun EmptyGalleryPlaceholder(
    onOpenCameraClick: () -> Unit,
) {
    GalleryPlaceholder(
        icon = Icons.Outlined.PhotoLibrary,
        title = stringResource(R.string.empty_gallery),
        description = stringResource(R.string.empty_gallery_description),
        action = {
            Button(onClick = onOpenCameraClick) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.gallery_open_camera))
            }
        },
    )
}

@Composable
private fun rememberBackgroundColor(
    uiState: UiState,
    fadeProgress: () -> Float,
): () -> Color {
    val dismissedBackground = MaterialTheme.cameraColors.onOverlay
    val background = animateViewerBackgroundAsState(
        areControlsVisible = uiState.showsDetails ||
            (uiState.areControlsVisible && !uiState.isZoomed),
    )

    return remember(background, dismissedBackground, fadeProgress) {
        {
            lerp(
                start = background.value,
                stop = dismissedBackground,
                fraction = fadeProgress(),
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
                        key = PREVIEW_PAGE_KEY,
                        name = "IMG_20231016_120000_000",
                        isVideo = false,
                    ),
                ),
                previews = persistentMapOf(
                    PREVIEW_PAGE_KEY to GalleryPreview.Loaded(
                        previewShot(
                            top = PREVIEW_COOL_ICON,
                            bottom = PREVIEW_SCENE,
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
