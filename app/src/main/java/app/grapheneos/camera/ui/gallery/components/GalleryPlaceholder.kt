package app.grapheneos.camera.ui.gallery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme

private val PLACEHOLDER_PADDING = 24.dp
private val PLACEHOLDER_SPACING = 12.dp
private val ICON_SIZE = 48.dp

@Composable
internal fun GalleryPlaceholder(
    isVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PLACEHOLDER_PADDING),
        verticalArrangement = Arrangement.spacedBy(
            space = PLACEHOLDER_SPACING,
            alignment = Alignment.CenterVertically,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = when {
                isVideo -> Icons.Outlined.VideocamOff
                else -> Icons.Outlined.BrokenImage
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ICON_SIZE),
        )
        Text(
            text = stringResource(
                when {
                    isVideo -> R.string.inaccessible_video
                    else -> R.string.inaccessible_image
                },
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewLightDark
@Composable
private fun GalleryPlaceholderPreview() {
    CameraPreviewTheme {
        GalleryPlaceholder(isVideo = false)
    }
}
