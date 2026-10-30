package app.grapheneos.camera.ui.statusbar.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.ui.core.effect.CollectEvents
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.core.theme.SelfIlluminationTheme
import app.grapheneos.camera.ui.statusbar.components.StatusBar
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarAction as Action
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarUiState
import app.grapheneos.camera.ui.statusbar.screen.model.StatusItemUiModel
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf

private val PREVIEW_HEIGHT = 62.dp

private val PREVIEW_PHOTO_UI_STATE = StatusBarUiState(
    items = persistentListOf(
        StatusItemUiModel.Flash(mode = FlashMode.AUTO),
        StatusItemUiModel.SelfTimer(duration = 5.seconds),
    ),
)

private val PREVIEW_VIDEO_UI_STATE = StatusBarUiState(
    items = persistentListOf(
        StatusItemUiModel.VideoLight,
        StatusItemUiModel.Resolution(quality = VideoQuality.FHD),
    ),
)

@Composable
internal fun StatusBarScreen(
    screenModel: StatusBarScreenModel,
    enabled: Boolean,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()
    val effectHandler = rememberStatusBarEffectHandler()

    CollectEvents(
        events = screenModel.effects,
        onEvent = effectHandler::handle,
    )

    StatusBarContent(
        uiState = uiState,
        enabled = enabled,
        onItemClick = onItemClick,
        onAction = screenModel::onAction,
        modifier = modifier,
    )
}

@Composable
private fun StatusBarContent(
    uiState: StatusBarUiState,
    enabled: Boolean,
    onItemClick: () -> Unit,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    SelfIlluminationTheme(
        selfIlluminating = uiState.isSelfIlluminating,
    ) {
        StatusBar(
            items = uiState.items,
            enabled = enabled,
            onItemClick = onItemClick,
            onSettingsClick = { onAction(Action.CameraSettingsClicked) },
            modifier = modifier,
        )
    }
}

@PreviewLightDark
@Composable
private fun StatusBarPhotoPreview() {
    CameraPreviewTheme {
        StatusBarContent(
            uiState = PREVIEW_PHOTO_UI_STATE,
            enabled = true,
            onItemClick = {},
            onAction = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT),
        )
    }
}

@PreviewLightDark
@Composable
private fun StatusBarVideoPreview() {
    CameraPreviewTheme {
        StatusBarContent(
            uiState = PREVIEW_VIDEO_UI_STATE,
            enabled = true,
            onItemClick = {},
            onAction = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT),
        )
    }
}
