package app.grapheneos.camera.ui.viewfinder.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.ui.components.motion.ProvideContentRotation

@Composable
internal fun ViewfinderOverlay(
    screenModel: ViewfinderScreenModel,
    content: @Composable () -> Unit,
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()

    ProvideContentRotation(
        degrees = uiState.iconRotationDegrees,
        content = content,
    )
    ViewfinderDialogs(
        dialog = uiState.dialog,
        onAction = screenModel::onAction,
    )
}
