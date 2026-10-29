package app.grapheneos.camera.ui.quicksettings.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.ui.core.animation.SETTLE_OFFSET_SPEC
import app.grapheneos.camera.ui.core.effect.CollectEvents
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsHeader
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsRows
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsSheet
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsTheme
import app.grapheneos.camera.ui.quicksettings.screen.mapper.QuickSettingsUiStateMapperImpl
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction as Action
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsInsets
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsState
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState

private val PREVIEW_INSETS = QuickSettingsInsets(
    previewBottom = 220.dp,
    shutterRow = 160.dp,
)

private val PREVIEW_FRONT_PHOTO_CAMERA = BoundCamera(
    mode = CameraMode.CAMERA,
    isVideoMode = false,
    lensFacing = LensFacing.FRONT,
    hasFlashUnit = false,
    videoQualities = emptyList(),
)

private val PREVIEW_VIDEO_CAMERA = BoundCamera(
    mode = CameraMode.VIDEO,
    isVideoMode = true,
    lensFacing = LensFacing.BACK,
    hasFlashUnit = true,
    videoQualities = listOf(VideoQuality.UHD, VideoQuality.FHD, VideoQuality.HD),
)

@Composable
internal fun QuickSettingsScreen(
    screenModel: QuickSettingsScreenModel,
    visible: Boolean,
    insets: QuickSettingsInsets,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()
    val effectHandler = rememberQuickSettingsEffectHandler()

    CollectEvents(
        events = screenModel.effects,
        onEvent = effectHandler::handle,
    )

    BackHandler(
        enabled = visible,
        onBack = onDismissRequest,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.previewTop)
            .clipToBounds(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(animationSpec = SETTLE_OFFSET_SPEC) { height -> height },
            exit = slideOutVertically(animationSpec = SETTLE_OFFSET_SPEC) { height -> height },
        ) {
            QuickSettingsContent(
                uiState = uiState,
                insets = insets,
                onAction = screenModel::onAction,
            )
        }
    }
}

@Composable
private fun QuickSettingsContent(
    uiState: QuickSettingsUiState,
    insets: QuickSettingsInsets,
    onAction: (Action) -> Unit,
) {
    QuickSettingsTheme(
        selfIlluminating = uiState.isSelfIlluminating,
    ) {
        QuickSettingsSheet(
            solidHeight = insets.previewBottom - insets.shutterRow,
            tailHeight = insets.shutterRow,
        ) {
            Column(
                modifier = Modifier.verticalScroll(state = rememberScrollState()),
            ) {
                QuickSettingsHeader(
                    isVideoMode = uiState.isVideoMode,
                    onMoreClick = { onAction(Action.CameraSettingsClicked) },
                )
                QuickSettingsRows(
                    uiState = uiState,
                    onAction = onAction,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun QuickSettingsPhotoPreview() {
    CameraPreviewTheme {
        QuickSettingsContent(
            uiState = previewUiState(camera = PREVIEW_FRONT_PHOTO_CAMERA),
            insets = PREVIEW_INSETS,
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun QuickSettingsVideoPreview() {
    CameraPreviewTheme {
        QuickSettingsContent(
            uiState = previewUiState(camera = PREVIEW_VIDEO_CAMERA),
            insets = PREVIEW_INSETS,
            onAction = {},
        )
    }
}

@Preview(widthDp = 320, heightDp = 400, fontScale = 2f)
@Composable
private fun QuickSettingsLargeFontPreview() {
    CameraPreviewTheme {
        QuickSettingsContent(
            uiState = previewUiState(camera = PREVIEW_VIDEO_CAMERA),
            insets = PREVIEW_INSETS,
            onAction = {},
        )
    }
}

private fun previewUiState(camera: BoundCamera): QuickSettingsUiState {
    return QuickSettingsUiStateMapperImpl().map(QuickSettingsState(boundCamera = camera))
}
