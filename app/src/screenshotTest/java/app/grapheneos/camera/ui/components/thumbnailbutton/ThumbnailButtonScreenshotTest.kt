package app.grapheneos.camera.ui.components.thumbnailbutton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.progress.model.RingProgress
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_COOL_ICON
import app.grapheneos.camera.ui.core.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.previewShot
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun ThumbnailButtonOverImage() {
    CameraPreviewColumn {
        ThumbnailButtonFlow(colors = ThumbnailButtonColors.fromTheme())
    }
}

@PreviewTest
@PreviewLightDark
@Composable
private fun ThumbnailButtonOnSurface() {
    CameraPreviewColumn {
        ThumbnailButtonFlow(colors = ThumbnailButtonColors.fromSurfaceTheme())
    }
}

@Composable
private fun ThumbnailButtonFlow(
    colors: ThumbnailButtonColors,
) {
    val shot = remember {
        previewShot(
            top = PREVIEW_COOL_ICON,
            bottom = PREVIEW_SCENE,
        )
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        listOf(null, shot).forEach { image ->
            listOf(true, false).forEach { enabled ->
                ThumbnailButton(
                    image = image,
                    onClick = {},
                    enabled = enabled,
                    colors = colors,
                )
            }
        }
        listOf(
            RingProgress.Indeterminate,
            RingProgress.Determinate(fraction = { 0.6f }),
            RingProgress.Segmented(
                segments = 3,
                fraction = { 2f / 3 },
            ),
        ).forEach { progress ->
            ThumbnailButton(
                image = shot,
                onClick = {},
                progress = progress,
                colors = colors,
            )
        }
    }
}
