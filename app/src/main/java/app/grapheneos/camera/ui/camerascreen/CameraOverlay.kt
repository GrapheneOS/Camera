package app.grapheneos.camera.ui.camerascreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import app.grapheneos.camera.ui.core.model.QuickSetting
import app.grapheneos.camera.ui.core.theme.SelfIlluminationTheme
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsSheetState
import app.grapheneos.camera.ui.quicksettings.navigation.QuickSettingsNavKey
import app.grapheneos.camera.ui.quicksettings.screen.QuickSettingsScreen
import app.grapheneos.camera.ui.quicksettings.screen.QuickSettingsScreenModel
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsInsets
import app.grapheneos.camera.ui.statusbar.screen.StatusBarScreen
import app.grapheneos.camera.ui.statusbar.screen.StatusBarScreenModel
import app.grapheneos.camera.ui.viewfinder.components.ViewfinderBottomRow
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderOverlay
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderScreenModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState

@Composable
internal fun CameraOverlay(
    viewfinder: ViewfinderScreenModel,
    statusBar: StatusBarScreenModel,
    quickSettings: QuickSettingsScreenModel,
    sheetState: QuickSettingsSheetState,
    sheet: QuickSettingsNavKey?,
    insets: CameraOverlayInsets,
    onSheetOpenChange: (open: Boolean) -> Unit,
) {
    val isSheetHidden by remember(sheetState) {
        derivedStateOf { sheetState.visibleFraction() == 0f }
    }

    ViewfinderOverlay(screenModel = viewfinder) { uiState ->
        val controlsShown = uiState.isSettingsPanelAvailable &&
            !uiState.captureResult.visible &&
            !uiState.preview.lastFrameVisible

        Box(modifier = Modifier.fillMaxSize()) {
            if (controlsShown) {
                StatusBarLayer(
                    statusBar = statusBar,
                    sheetState = sheetState,
                    enabled = isSheetHidden,
                    height = insets.previewTop,
                    onItemClick = { setting ->
                        viewfinder.onAction(SettingsAction.StatusBarClicked(setting))
                    },
                )
                BottomRowLayer(
                    uiState = uiState,
                    insets = insets,
                    onQuickSettingsClick = {
                        if (isSheetHidden) {
                            viewfinder.onAction(SettingsAction.QuickSettingsButtonClicked)
                        }
                    },
                )
            }
            QuickSettingsScreen(
                screenModel = quickSettings,
                sheetState = sheetState,
                visible = sheet != null,
                insets = QuickSettingsInsets(
                    previewTop = insets.previewTop,
                    previewBottom = insets.previewBottom,
                    shutterRow = insets.shutterRow,
                ),
                highlight = sheet?.highlight,
                onVisibleChange = onSheetOpenChange,
            )
        }
    }
}

@Composable
private fun StatusBarLayer(
    statusBar: StatusBarScreenModel,
    sheetState: QuickSettingsSheetState,
    enabled: Boolean,
    height: Dp,
    onItemClick: (QuickSetting) -> Unit,
) {
    StatusBarScreen(
        screenModel = statusBar,
        enabled = enabled,
        onItemClick = onItemClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { alpha = 1f - sheetState.visibleFraction() },
    )
}

@Composable
private fun BoxScope.BottomRowLayer(
    uiState: ViewfinderUiState,
    insets: CameraOverlayInsets,
    onQuickSettingsClick: () -> Unit,
) {
    SelfIlluminationTheme(
        selfIlluminating = uiState.isSelfIlluminating,
    ) {
        ViewfinderBottomRow(
            isVideoMode = uiState.mode.isVideo,
            onQuickSettingsClick = onQuickSettingsClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = insets.bottomRow),
        )
    }
}
