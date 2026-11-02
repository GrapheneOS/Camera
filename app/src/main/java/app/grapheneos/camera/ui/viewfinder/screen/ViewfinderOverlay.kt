package app.grapheneos.camera.ui.viewfinder.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.ui.components.motion.ProvideContentRotation
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState

@Composable
internal fun ViewfinderOverlay(
    screenModel: ViewfinderScreenModel,
    content: @Composable (uiState: ViewfinderUiState) -> Unit,
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()

    ProvideContentRotation(
        degrees = uiState.rotation.iconDegrees,
    ) {
        content(uiState)
    }
    ViewfinderDialogs(
        dialog = uiState.dialog,
        onAction = screenModel::onAction,
    )
}
