package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.compositiongrid.CompositionGrid
import app.grapheneos.camera.ui.components.compositiongrid.CompositionGridColors
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn

private val ILLUSTRATION_WIDTH = 48.dp
private val ILLUSTRATION_HEIGHT = 36.dp

@Composable
internal fun GridIllustration(
    pattern: CompositionGridPattern?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(
                width = ILLUSTRATION_WIDTH,
                height = ILLUSTRATION_HEIGHT,
            )
            .clip(ShapeDefaults.ExtraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (pattern != null) {
            CompositionGrid(
                pattern = pattern,
                modifier = Modifier.matchParentSize(),
                colors = CompositionGridColors(
                    lineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun GridIllustrationPreview() {
    CameraPreviewColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GridIllustration(pattern = null)
            CompositionGridPattern.entries.forEach { pattern ->
                GridIllustration(pattern = pattern)
            }
        }
    }
}
