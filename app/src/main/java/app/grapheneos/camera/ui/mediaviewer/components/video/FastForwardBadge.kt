package app.grapheneos.camera.ui.mediaviewer.components.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.core.theme.cameraColors

private val BADGE_HORIZONTAL_PADDING = 12.dp
private val BADGE_VERTICAL_PADDING = 6.dp
private val BADGE_SPACING = 4.dp
private val BADGE_ICON_SIZE = 18.dp

@Composable
internal fun FastForwardBadge(
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.cameraColors

    Row(
        horizontalArrangement = Arrangement.spacedBy(BADGE_SPACING),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                color = colors.overlayScrim,
                shape = CircleShape,
            )
            .padding(
                horizontal = BADGE_HORIZONTAL_PADDING,
                vertical = BADGE_VERTICAL_PADDING,
            ),
    ) {
        Text(
            text = stringResource(R.string.video_fast_forward_speed),
            color = colors.overlay,
            style = MaterialTheme.typography.labelLarge,
        )
        Icon(
            imageVector = Icons.Filled.FastForward,
            contentDescription = null,
            tint = colors.overlay,
            modifier = Modifier.size(BADGE_ICON_SIZE),
        )
    }
}

@PreviewLightDark
@Composable
private fun FastForwardBadgePreview() {
    CameraPreviewTheme {
        FastForwardBadge()
    }
}
