package app.grapheneos.camera.ui.statusbar.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.core.theme.SelfIlluminationTheme
import app.grapheneos.camera.ui.quicksettings.navigation.QuickSetting
import app.grapheneos.camera.ui.statusbar.components.StatusBar
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
        StatusItemUiModel.Flash(mode = FlashMode.ON),
        StatusItemUiModel.Resolution(quality = VideoQuality.FHD),
    ),
)

@Composable
internal fun StatusBarScreen(
    screenModel: StatusBarScreenModel,
    enabled: Boolean,
    onItemClick: (QuickSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()

    StatusBarContent(
        uiState = uiState,
        enabled = enabled,
        onItemClick = onItemClick,
        modifier = modifier,
    )
}

@Composable
internal fun StatusBarContent(
    uiState: StatusBarUiState,
    enabled: Boolean,
    onItemClick: (QuickSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: follow the system theme once the viewfinder's black background does.
    SelfIlluminationTheme(
        selfIlluminating = uiState.isSelfIlluminating,
        darkTheme = true,
    ) {
        StatusBar(
            items = uiState.items,
            enabled = enabled,
            onItemClick = onItemClick,
            modifier = modifier,
        )
    }
}

@Preview
@Composable
private fun StatusBarPhotoPreview() {
    CameraPreviewTheme(darkTheme = true) {
        StatusBarContent(
            uiState = PREVIEW_PHOTO_UI_STATE,
            enabled = true,
            onItemClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT),
        )
    }
}

@Preview
@Composable
private fun StatusBarVideoPreview() {
    CameraPreviewTheme(darkTheme = true) {
        StatusBarContent(
            uiState = PREVIEW_VIDEO_UI_STATE,
            enabled = true,
            onItemClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT),
        )
    }
}
