package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.CameraPreviewViewfinder
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun LevelIndicatorAngles() {
    CameraPreviewColumn {
        CameraPreviewViewfinder {
            FlowRow(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
                verticalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                LevelIndicator(
                    roll = { -22f },
                    pitch = { -30f },
                )
                LevelIndicator(
                    roll = { -23f },
                    pitch = { 0f },
                )
                LevelIndicator(
                    roll = { 0f },
                    pitch = { 20f },
                )
                LevelIndicator(
                    roll = { 0f },
                    pitch = { 0f },
                )
                LevelIndicator(
                    roll = { 150f },
                    pitch = { -70f },
                )
                LevelIndicator(
                    roll = { 0f },
                    pitch = { -90f },
                )
            }
        }
    }
}
