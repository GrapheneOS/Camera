package app.grapheneos.camera.ui.gallery.components.pager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme

private val PLACEHOLDER_PADDING = 24.dp
private val ICON_CONTAINER_SIZE = 96.dp
private val ICON_SIZE = 48.dp
private val ICON_SPACING = 16.dp
private val TEXT_SPACING = 8.dp
private val ACTION_SPACING = 24.dp

@Composable
internal fun GalleryPlaceholder(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    fileName: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PLACEHOLDER_PADDING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ICON_CONTAINER_SIZE),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
        }
        Spacer(modifier = Modifier.height(ICON_SPACING))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (fileName != null) {
            Spacer(modifier = Modifier.height(TEXT_SPACING))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(TEXT_SPACING))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(ACTION_SPACING))
            action()
        }
    }
}

@PreviewLightDark
@Composable
private fun GalleryPlaceholderEmptyPreview() {
    CameraPreviewTheme {
        GalleryPlaceholder(
            icon = Icons.Outlined.PhotoLibrary,
            title = "No photos or videos yet",
            description = "Photos and videos you take appear here",
            action = {
                Button(onClick = {}) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text(text = "Open camera")
                }
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun GalleryPlaceholderUnavailablePreview() {
    CameraPreviewTheme {
        GalleryPlaceholder(
            icon = Icons.Outlined.BrokenImage,
            title = "This photo can't be opened",
            description = "It may have been moved, deleted or damaged",
            fileName = "IMG_20231016_120000_000",
        )
    }
}
