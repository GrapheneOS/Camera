package app.grapheneos.camera.ui.viewfinder.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.components.overlayiconbutton.OverlayIconButton
import app.grapheneos.camera.ui.components.overlayiconbutton.OverlayIconButtonColors
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.core.theme.cameraColors

private val HORIZONTAL_PADDING = 16.dp

@Composable
internal fun ViewfinderBottomRow(
    isVideoMode: Boolean,
    onQuickSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraColors = MaterialTheme.cameraColors
    val description = when {
        isVideoMode -> stringResource(R.string.quick_settings_video_title)
        else -> stringResource(R.string.quick_settings_photo_title)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HORIZONTAL_PADDING),
    ) {
        OverlayIconButton(
            onClick = onQuickSettingsClick,
            icon = Icons.Outlined.Tune,
            modifier = Modifier.semantics { contentDescription = description },
            colors = OverlayIconButtonColors(
                containerColor = cameraColors.controlContainer,
                contentColor = cameraColors.onControlContainer,
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun ViewfinderBottomRowPreview() {
    CameraPreviewTheme {
        ViewfinderBottomRow(
            isVideoMode = false,
            onQuickSettingsClick = {},
        )
    }
}
