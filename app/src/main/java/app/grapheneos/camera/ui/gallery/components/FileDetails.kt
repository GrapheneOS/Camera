package app.grapheneos.camera.ui.gallery.components

import android.content.Context
import android.text.format.Formatter
import android.util.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.CapturedItemDetails
import app.grapheneos.camera.data.media.model.MediaTimestamp
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.util.formatVideoDuration
import app.grapheneos.camera.util.storageLocationToUiString
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

private val HORIZONTAL_PADDING = 24.dp
private val BOTTOM_PADDING = 24.dp
private val ROW_SPACING = 24.dp
private val ICON_TEXT_SPACING = 16.dp

@Composable
internal fun FileDetails(
    details: CapturedItemDetails,
    isVideo: Boolean,
    uri: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = HORIZONTAL_PADDING,
                end = HORIZONTAL_PADDING,
                bottom = BOTTOM_PADDING,
            ),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        Text(
            text = stringResource(R.string.gallery_details),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TimeRow(details = details)
        DetailsRow(
            icon = when {
                isVideo -> Icons.Outlined.Movie
                else -> Icons.Outlined.Image
            },
            title = details.fileName,
            subtitle = fileFacts(
                details = details,
                isVideo = isVideo,
            ),
        )
        DetailsRow(
            icon = Icons.Outlined.Folder,
            title = filePath(
                context = LocalContext.current,
                uri = uri,
                details = details,
            ),
        )
    }
}

@Composable
private fun TimeRow(details: CapturedItemDetails) {
    val locale = LocalLocale.current.platformLocale
    val zone = ZoneId.systemDefault()
    val created = details.created?.formatted(locale, zone)
    val modified = details.modified?.formatted(locale, zone)
    val title = created ?: modified ?: return
    val subtitle = modified
        ?.takeIf { created != null && it != created }
        ?.let { stringResource(R.string.gallery_details_modified, it) }

    DetailsRow(
        icon = Icons.Outlined.Event,
        title = title,
        subtitle = subtitle,
    )
}

@Composable
private fun DetailsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(ICON_TEXT_SPACING))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun fileFacts(
    details: CapturedItemDetails,
    isVideo: Boolean,
): String? {
    val context = LocalContext.current
    val resources = LocalResources.current
    val facts = listOfNotNull(
        details.resolution?.let {
            stringResource(R.string.gallery_details_resolution, it.width, it.height)
        },
        details.sizeBytes?.let {
            Formatter.formatFileSize(context, it)
        },
        details.duration
            ?.takeIf { isVideo }
            ?.let { formatVideoDuration(it.inWholeSeconds) },
    )

    return facts.reduceOrNull { joined, fact ->
        resources.getString(R.string.gallery_details_joined, joined, fact)
    }
}

private fun filePath(
    context: Context,
    uri: String,
    details: CapturedItemDetails,
): String {
    return when (val relativePath = details.relativePath) {
        null -> storageLocationToUiString(context, uri)
        else -> "${context.getString(R.string.main_storage)}/$relativePath${details.fileName}"
    }
}

@PreviewLightDark
@Composable
private fun FileDetailsPreview() {
    val dateTime = LocalDateTime.of(2023, 10, 16, 12, 0)

    CameraPreviewTheme {
        Surface {
            FileDetails(
                details = CapturedItemDetails(
                    fileName = "VID_20231016_120000_000.mp4",
                    relativePath = "DCIM/Camera/",
                    sizeBytes = 12_345_678,
                    resolution = Size(1920, 1080),
                    duration = 12.seconds,
                    created = MediaTimestamp.WallClock(dateTime),
                    modified = MediaTimestamp.WallClock(dateTime.plusMinutes(5)),
                ),
                isVideo = true,
                uri = "content://media/external/video/media/1",
            )
        }
    }
}
