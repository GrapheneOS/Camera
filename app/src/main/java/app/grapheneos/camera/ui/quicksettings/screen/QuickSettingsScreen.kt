package app.grapheneos.camera.ui.quicksettings.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.ui.core.effect.CollectEvents
import app.grapheneos.camera.ui.core.gesture.SETTLE_VELOCITY
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsHeader
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsRows
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsSheet
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsSheetState
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsTheme
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickChoiceUiModel
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction as Action
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsInsets
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private val PREVIEW_INSETS = QuickSettingsInsets(
    previewBottom = 220.dp,
    shutterRow = 160.dp,
)

private val PREVIEW_FRONT_PHOTO_UI_STATE = QuickSettingsUiState(
    selfIllumination = false,
    selfTimer = QuickChoiceUiModel(
        selected = Duration.ZERO,
        options = persistentListOf(Duration.ZERO, 3.seconds, 5.seconds, 10.seconds),
    ),
    aspectRatio = QuickChoiceUiModel(
        selected = AspectRatio.RATIO_4_3,
        options = AspectRatio.entries.toImmutableList(),
    ),
)

private val PREVIEW_VIDEO_UI_STATE = QuickSettingsUiState(
    isVideoMode = true,
    videoLight = false,
    videoQuality = QuickChoiceUiModel(
        selected = VideoQuality.UHD,
        options = persistentListOf(VideoQuality.UHD, VideoQuality.FHD, VideoQuality.HD),
    ),
    audio = true,
)

@Composable
internal fun QuickSettingsScreen(
    screenModel: QuickSettingsScreenModel,
    sheetState: QuickSettingsSheetState,
    visible: Boolean,
    insets: QuickSettingsInsets,
    onVisibleChange: (visible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()
    val effectHandler = rememberQuickSettingsEffectHandler()

    CollectEvents(
        events = screenModel.effects,
        onEvent = effectHandler::handle,
    )

    LaunchedEffect(sheetState, visible) {
        sheetState.show(visible)
    }

    BackHandler(
        enabled = visible,
        onBack = { onVisibleChange(false) },
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.previewTop)
            .clipToBounds(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        QuickSettingsContent(
            uiState = uiState,
            sheetState = sheetState,
            insets = insets,
            onSettle = onVisibleChange,
            onAction = screenModel::onAction,
        )
    }
}

@Composable
private fun QuickSettingsContent(
    uiState: QuickSettingsUiState,
    sheetState: QuickSettingsSheetState,
    insets: QuickSettingsInsets,
    onSettle: (isShown: Boolean) -> Unit,
    onAction: (Action) -> Unit,
) {
    QuickSettingsTheme(
        selfIlluminating = uiState.isSelfIlluminating,
    ) {
        QuickSettingsSheet(
            state = sheetState,
            solidHeight = insets.previewBottom - insets.shutterRow,
            tailHeight = insets.shutterRow,
            onSettle = onSettle,
        ) {
            Column(
                modifier = Modifier.verticalScroll(state = rememberScrollState()),
            ) {
                QuickSettingsHeader(
                    isVideoMode = uiState.isVideoMode,
                    onMoreClick = { onAction(Action.CameraSettingsClicked) },
                    onMoreLongClick = { onAction(Action.CameraSettingsLongClicked) },
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
            uiState = PREVIEW_FRONT_PHOTO_UI_STATE,
            sheetState = rememberPreviewSheetState(),
            insets = PREVIEW_INSETS,
            onSettle = {},
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun QuickSettingsVideoPreview() {
    CameraPreviewTheme {
        QuickSettingsContent(
            uiState = PREVIEW_VIDEO_UI_STATE,
            sheetState = rememberPreviewSheetState(),
            insets = PREVIEW_INSETS,
            onSettle = {},
            onAction = {},
        )
    }
}

@Preview(widthDp = 320, heightDp = 400, fontScale = 2f)
@Composable
private fun QuickSettingsLargeFontPreview() {
    CameraPreviewTheme {
        QuickSettingsContent(
            uiState = PREVIEW_VIDEO_UI_STATE,
            sheetState = rememberPreviewSheetState(),
            insets = PREVIEW_INSETS,
            onSettle = {},
            onAction = {},
        )
    }
}

@Composable
private fun rememberPreviewSheetState(): QuickSettingsSheetState {
    val density = LocalDensity.current

    return remember(density) {
        QuickSettingsSheetState(
            settleVelocity = with(density) { SETTLE_VELOCITY.toPx() },
            isShown = true,
        )
    }
}
