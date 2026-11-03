package app.grapheneos.camera.ui.camerascreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import app.grapheneos.camera.ui.components.motion.FADE_IN
import app.grapheneos.camera.ui.components.motion.FADE_OUT
import app.grapheneos.camera.ui.core.model.QuickSetting
import app.grapheneos.camera.ui.core.theme.SelfIlluminationTheme
import app.grapheneos.camera.ui.previewoverlays.screen.PreviewOverlaysScreen
import app.grapheneos.camera.ui.previewoverlays.screen.PreviewOverlaysScreenModel
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsSheetState
import app.grapheneos.camera.ui.quicksettings.navigation.QuickSettingsNavKey
import app.grapheneos.camera.ui.quicksettings.screen.QuickSettingsScreen
import app.grapheneos.camera.ui.quicksettings.screen.QuickSettingsScreenModel
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsInsets
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsNavEvent
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
    previewOverlays: PreviewOverlaysScreenModel,
    statusBar: StatusBarScreenModel,
    quickSettings: QuickSettingsScreenModel,
    sheetState: QuickSettingsSheetState,
    sheet: QuickSettingsNavKey?,
    insets: CameraOverlayInsets,
    onSheetOpenChange: (open: Boolean) -> Unit,
    onQuickSettingsNavigate: (QuickSettingsNavEvent) -> Unit,
) {
    ViewfinderOverlay(screenModel = viewfinder) { uiState ->
        val controlsShown = uiState.isSettingsPanelAvailable && !uiState.captureResult.visible
        val isModeSwitching = uiState.modeTabs.isSwitching
        val statusBarVisible = controlsShown && !uiState.recording.isActive && !isModeSwitching
        val controlsEnabled = sheetState.isHidden &&
            !uiState.preview.lastFrameVisible &&
            !isModeSwitching

        Box(modifier = Modifier.fillMaxSize()) {
            PreviewOverlaysScreen(
                previewBounds = insets.previewBounds,
                screenModel = previewOverlays,
            )

            AnimatedVisibility(
                visible = statusBarVisible,
                enter = FADE_IN,
                exit = FADE_OUT,
            ) {
                StatusBarLayer(
                    statusBar = statusBar,
                    sheetState = sheetState,
                    enabled = controlsEnabled && statusBarVisible,
                    height = insets.previewBounds.top,
                    onItemClick = { setting ->
                        viewfinder.onAction(SettingsAction.StatusBarClicked(setting))
                    },
                )
            }
            if (controlsShown) {
                BottomRowLayer(
                    uiState = uiState,
                    insets = insets,
                    onQuickSettingsClick = {
                        if (controlsEnabled) {
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
                    previewTop = insets.previewBounds.top,
                    previewBottom = insets.previewBottom,
                    shutterRow = insets.shutterRow,
                ),
                highlight = sheet?.highlight,
                onVisibleChange = onSheetOpenChange,
                onNavigate = onQuickSettingsNavigate,
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
