package app.grapheneos.camera.ui.viewfinder.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ViewfinderOverlay(screenModel: ViewfinderScreenModel) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()

    ViewfinderDialogs(
        dialog = uiState.dialog,
        onAction = screenModel::onAction,
    )
}
