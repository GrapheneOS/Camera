package app.grapheneos.camera.ui.previewoverlays.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.grapheneos.camera.ui.components.levelindicator.LevelIndicator
import app.grapheneos.camera.ui.components.motion.ProvideContentRotation
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn
import app.grapheneos.camera.ui.core.preview.CameraPreviewViewfinder
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.LevelUiState

@Composable
internal fun PreviewLevel(
    level: State<LevelUiState>,
    modifier: Modifier = Modifier,
) {
    val visible by remember(level) { derivedStateOf { level.value.visible } }
    val mode by remember(level) { derivedStateOf { level.value.mode } }
    val rotation by remember(level) { derivedStateOf { level.value.rotationDegrees } }

    if (visible) {
        ProvideContentRotation(degrees = rotation) {
            LevelIndicator(
                mode = mode,
                roll = { level.value.rollDegrees },
                pitch = { level.value.pitchDegrees },
                modifier = modifier,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewLevelPreview() {
    CameraPreviewColumn {
        CameraPreviewViewfinder {
            PreviewLevel(
                level = rememberUpdatedState(
                    LevelUiState(
                        visible = true,
                        rollDegrees = -4f,
                        pitchDegrees = 2f,
                    ),
                ),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
