package app.grapheneos.camera.ui.gallery.components.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDetails

private val PLACEHOLDER_PADDING = 24.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailsPanelContent(
    details: GalleryDetails,
    isVideo: Boolean,
    uri: String,
    showsDragHandle: Boolean,
    dragHandleAlpha: () -> Float,
    onOpenMapClick: (() -> Unit)?,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (showsDragHandle) {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .graphicsLayer { alpha = dragHandleAlpha() },
            )
        }

        when (details) {
            is GalleryDetails.Loading -> Unit

            is GalleryDetails.Loaded -> FileDetails(
                details = details.details,
                isVideo = isVideo,
                uri = uri,
                onOpenMapClick = onOpenMapClick,
                onCopy = onCopy,
            )

            is GalleryDetails.Unavailable -> Text(
                text = stringResource(R.string.unable_to_obtain_file_details),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(PLACEHOLDER_PADDING),
            )
        }
    }
}
