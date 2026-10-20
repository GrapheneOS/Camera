package app.grapheneos.camera.ui.gallery.components

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import android.util.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemDetails
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.model.MediaTimestamp
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.util.formatVideoDuration
import app.grapheneos.camera.util.storageLocationToUiString
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

private val DETAILS_SPACING = 16.dp

@Composable
internal fun DetailsDialog(
    item: CapturedItem,
    details: CapturedItemDetails,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val zone = ZoneId.systemDefault()
    val notFound = stringResource(R.string.not_found_generic)
    val fileSize = details.sizeBytes?.let { Formatter.formatFileSize(context, it) }
    val duration = details.duration?.let { formatVideoDuration(it.inWholeSeconds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.file_details)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(DETAILS_SPACING),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                DetailsRow(
                    label = stringResource(R.string.file_name_generic),
                    value = details.fileName,
                )
                DetailsRow(
                    label = stringResource(R.string.file_path),
                    value = filePath(
                        context = context,
                        uri = item.uri,
                        details = details,
                    ),
                )
                DetailsRow(
                    label = stringResource(R.string.file_size),
                    value = fileSize ?: notFound,
                )
                DetailsRow(
                    label = stringResource(R.string.resolution),
                    value = details.resolution?.toString() ?: notFound,
                )
                if (item.type == CapturedItemType.VIDEO) {
                    DetailsRow(
                        label = stringResource(R.string.duration),
                        value = duration ?: notFound,
                    )
                }
                DetailsRow(
                    label = stringResource(R.string.file_created_on),
                    value = details.created?.formatted(locale, zone) ?: notFound,
                )
                DetailsRow(
                    label = stringResource(R.string.last_modified_on),
                    value = details.modified?.formatted(locale, zone) ?: notFound,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun DetailsRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun filePath(
    context: Context,
    uri: Uri,
    details: CapturedItemDetails,
): String {
    return when (val relativePath = details.relativePath) {
        null -> storageLocationToUiString(context, uri.toString())
        else -> "${context.getString(R.string.main_storage)}/$relativePath${details.fileName}"
    }
}

@PreviewLightDark
@Composable
private fun DetailsDialogPreview() {
    val dateTime = LocalDateTime.of(2023, 10, 16, 12, 0)

    CameraPreviewTheme {
        DetailsDialog(
            item = CapturedItem(
                type = CapturedItemType.VIDEO,
                dateString = "20231016_120000_000",
                uri = Uri.EMPTY,
            ),
            details = CapturedItemDetails(
                fileName = "VID_20231016_120000_000.mp4",
                relativePath = "DCIM/Camera/",
                sizeBytes = 12_345_678,
                resolution = Size(1920, 1080),
                duration = 12.seconds,
                created = MediaTimestamp.WallClock(dateTime),
                modified = MediaTimestamp.WallClock(dateTime),
            ),
            onDismiss = {},
        )
    }
}
