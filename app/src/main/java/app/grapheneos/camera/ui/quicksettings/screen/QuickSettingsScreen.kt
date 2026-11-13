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
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.ui.core.effect.CollectEvents
import app.grapheneos.camera.ui.core.theme.SelfIlluminationTheme
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsHeader
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsRows
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsSheet
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsSheetState
import app.grapheneos.camera.ui.quicksettings.components.RowHighlightState
import app.grapheneos.camera.ui.quicksettings.components.quickSettingsTitle
import app.grapheneos.camera.ui.quicksettings.navigation.QuickSetting
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction as Action
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction.RequiresIdleRecorder
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsInsets
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState

@Composable
internal fun QuickSettingsScreen(
    screenModel: QuickSettingsScreenModel,
    sheetState: QuickSettingsSheetState,
    visible: Boolean,
    insets: QuickSettingsInsets,
    highlight: QuickSetting?,
    onVisibleChange: (visible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()
    val effectHandler = rememberQuickSettingsEffectHandler()
    val rowHighlight = remember { RowHighlightState() }

    CollectEvents(
        events = screenModel.effects,
        onEvent = effectHandler::handle,
    )

    LaunchedEffect(sheetState, visible) {
        sheetState.show(visible)
    }

    LaunchedEffect(highlight) {
        if (highlight == null) return@LaunchedEffect

        sheetState.awaitShown()
        rowHighlight.blink(highlight)
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
            rowHighlight = rowHighlight,
            insets = insets,
            onSettle = onVisibleChange,
            onAction = screenModel::onAction,
        )
    }

    QuickSettingsDialogs(
        dialog = uiState.dialog,
        onAction = screenModel::onAction,
    )
}

@Composable
internal fun QuickSettingsContent(
    uiState: QuickSettingsUiState,
    sheetState: QuickSettingsSheetState,
    rowHighlight: RowHighlightState,
    insets: QuickSettingsInsets,
    onSettle: (isShown: Boolean) -> Unit,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    SelfIlluminationTheme(
        selfIlluminating = uiState.isSelfIlluminating,
    ) {
        QuickSettingsSheet(
            state = sheetState,
            solidHeight = insets.previewBottom - insets.shutterRow,
            tailHeight = insets.shutterRow,
            onSettle = onSettle,
            modifier = modifier,
        ) { enabled ->
            val title = quickSettingsTitle(isVideoMode = uiState.isVideoMode)

            Column(
                modifier = Modifier
                    .verticalScroll(
                        state = rememberScrollState(),
                        enabled = enabled,
                    )
                    .semantics { paneTitle = title },
            ) {
                QuickSettingsHeader(
                    isVideoMode = uiState.isVideoMode,
                    moreButtonVisible = uiState.moreButtonVisible,
                    enabled = enabled,
                    onMoreClick = { onAction(RequiresIdleRecorder.CameraSettingsClicked) },
                    onMoreLongClick = { onAction(RequiresIdleRecorder.CameraSettingsLongClicked) },
                )
                QuickSettingsRows(
                    uiState = uiState,
                    rowHighlight = rowHighlight,
                    enabled = enabled,
                    onAction = onAction,
                )
            }
        }
    }
}
