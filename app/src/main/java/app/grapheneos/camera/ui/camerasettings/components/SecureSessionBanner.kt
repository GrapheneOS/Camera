package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn

@Composable
internal fun SecureSessionBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                start = ITEM_START_PADDING,
                top = ITEM_VERTICAL_PADDING,
                end = ITEM_END_PADDING,
                bottom = ITEM_VERTICAL_PADDING,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(ITEM_ICON_SPACING))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SecureSessionBannerPreview() {
    CameraPreviewColumn {
        SecureSessionBanner(
            text = "The camera was opened from the lock screen. Changes made here are not kept.",
        )
    }
}
