package app.grapheneos.camera.ui.components.compositiongrid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.CameraPreviewViewfinder
import app.grapheneos.camera.ui.core.PREVIEW_ASPECT_RATIO
import app.grapheneos.camera.ui.core.PREVIEW_TALL_ASPECT_RATIO
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun CompositionGridPatterns() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            CompositionGridRow(aspectRatio = PREVIEW_ASPECT_RATIO)
            CompositionGridRow(aspectRatio = PREVIEW_TALL_ASPECT_RATIO)
        }
    }
}

@Composable
private fun CompositionGridRow(
    aspectRatio: Float,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        CompositionGridPattern.entries.forEach { pattern ->
            CameraPreviewViewfinder(
                modifier = Modifier.weight(weight = 1f),
                aspectRatio = aspectRatio,
            ) {
                CompositionGrid(
                    pattern = pattern,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}
