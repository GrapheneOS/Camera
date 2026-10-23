package app.grapheneos.camera.ui.gallery.screen

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.effect.CollectEvents
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.core.preview.PREVIEW_COOL_ICON
import app.grapheneos.camera.ui.core.preview.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.preview.PreviewScreenLightDark
import app.grapheneos.camera.ui.core.preview.previewShot
import app.grapheneos.camera.ui.core.theme.cameraColors
import app.grapheneos.camera.ui.gallery.components.details.animateDetailsPositionAsState
import app.grapheneos.camera.ui.gallery.components.details.rememberDetailsPanelState
import app.grapheneos.camera.ui.gallery.components.pager.GalleryPlaceholder
import app.grapheneos.camera.ui.gallery.components.pager.cameraPageProgress
import app.grapheneos.camera.ui.gallery.components.pager.gesture.rememberViewerTransformState
import app.grapheneos.camera.ui.gallery.components.pager.rememberGalleryPagerState
import app.grapheneos.camera.ui.gallery.components.pager.rememberViewerTransform
import app.grapheneos.camera.ui.gallery.components.pager.settledItemIndex
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryBarsUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryMessage
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPagerUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryUiState as UiState
import app.grapheneos.camera.ui.mediaviewer.components.HdrColorModeEffect
import app.grapheneos.camera.ui.mediaviewer.components.SystemBarsVisibilityEffect
import app.grapheneos.camera.ui.mediaviewer.components.animateViewerBackgroundAsState
import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncher
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.time.Duration
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

private const val PREVIEW_PAGE_KEY = "content://media/external/images/media/1"

@Composable
internal fun GalleryScreen(
    systemGalleryLauncher: SystemGalleryLauncher,
    clipboardManager: ClipboardManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    screenModel: GalleryScreenModel = hiltViewModel<GalleryViewModel>(),
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()
    val videoPosition = screenModel.videoPosition.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<GalleryMessage?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val effectHandler = rememberGalleryEffectHandler(
        systemGalleryLauncher = systemGalleryLauncher,
        clipboardManager = clipboardManager,
        onShowMessage = { shownMessage -> message = shownMessage },
    )

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        screenModel.onAction(Action.ScreenStopped)
    }

    CollectEvents(
        events = screenModel.effects,
        onEvent = effectHandler::handle,
    )

    GalleryMessageEffect(
        message = message,
        trashedPageKey = uiState.trashedPageKey,
        snackbarHostState = snackbarHostState,
        onUndoClick = { screenModel.onAction(Action.UndoClicked) },
        onUndoDismiss = { screenModel.onAction(Action.UndoDismissed) },
    )

    SystemBarsVisibilityEffect(
        visible = uiState.chrome.showsSystemBars,
    )

    HdrColorModeEffect(
        isHdr = uiState.showsHdr,
    )

    GalleryDialogs(
        dialog = uiState.dialog,
        onAction = screenModel::onAction,
    )

    GalleryContent(
        uiState = uiState,
        videoPosition = { videoPosition.value },
        onAction = screenModel::onAction,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier.then(
            when {
                uiState.video?.isPlaying() == true -> Modifier.keepScreenOn()
                else -> Modifier
            },
        ),
    )
}

@Composable
private fun GalleryContent(
    uiState: UiState,
    videoPosition: () -> Duration,
    onAction: (Action) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberGalleryPagerState(
        currentPage = uiState.pager.currentPage,
        itemCount = { uiState.pager.pages.size },
    )
    val settledPageKey = uiState.pager.pages.getOrNull(pagerState.settledItemIndex)?.key
    val transformState = rememberViewerTransformState(pageKey = settledPageKey)
    val detailsState = rememberDetailsPanelState(isShown = uiState.showsDetails)
    val detailsPosition = animateDetailsPositionAsState(detailsState)
    val isPanelVisible by remember(detailsPosition) {
        derivedStateOf { detailsPosition.value > 0f }
    }
    val viewerTransform = rememberViewerTransform(
        transformState = transformState,
        onDismissed = onNavigateBack,
    )
    val backgroundColor = rememberBackgroundColor(
        isImmersive = uiState.chrome.isImmersive,
        fadeProgress = viewerTransform::fadeProgress,
    )

    GalleryViewerEffects(
        pageKey = settledPageKey,
        transformState = transformState,
        detailsState = detailsState,
        showsDetails = uiState.showsDetails,
        onAction = onAction,
    )

    GalleryScaffold(
        uiState = uiState,
        fadeProgress = { max(viewerTransform.fadeProgress(), pagerState.cameraPageProgress) },
        isPanelVisible = isPanelVisible,
        backButtonVisibility = { detailsState.backButtonVisibilityAt(detailsPosition.value) },
        backgroundColor = backgroundColor,
        onAction = onAction,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(backgroundColor()) },
    ) { contentPadding ->
        GalleryViewerPager(
            uiState = uiState,
            videoPosition = videoPosition,
            pagerState = pagerState,
            transformState = transformState,
            viewerTransform = viewerTransform,
            detailsState = detailsState,
            detailsPosition = { detailsPosition.value },
            isPanelVisible = isPanelVisible,
            contentPadding = contentPadding,
            onAction = onAction,
            onNavigateBack = onNavigateBack,
        )

        if (uiState.pager.isEmpty) {
            EmptyGalleryPlaceholder(
                onOpenCameraClick = onNavigateBack,
            )
        }
    }
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
    isImmersive: Boolean,
    fadeProgress: () -> Float,
): () -> Color {
    val dismissedBackground = MaterialTheme.cameraColors.onOverlay
    val background = animateViewerBackgroundAsState(
        areControlsVisible = !isImmersive,
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

@PreviewScreenLightDark
@Composable
private fun GalleryContentPreview() {
    CameraPreviewTheme {
        GalleryContent(
            uiState = UiState(
                pager = GalleryPagerUiModel(
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
                ),
                bars = GalleryBarsUiModel(
                    capturedAt = LocalDateTime.of(2023, 10, 16, 12, 0),
                    showsItemActions = true,
                ),
            ),
            videoPosition = { Duration.ZERO },
            onAction = {},
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
