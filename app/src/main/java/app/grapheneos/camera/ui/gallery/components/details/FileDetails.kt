package app.grapheneos.camera.ui.gallery.components.details

import android.text.format.DateFormat
import android.util.Size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.CameraParameters
import app.grapheneos.camera.data.media.model.CapturedItemDetails
import app.grapheneos.camera.data.media.model.GeoCoordinates
import app.grapheneos.camera.data.media.model.MediaTimestamp
import app.grapheneos.camera.ui.common.components.copyOnLongClick
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.gallery.components.formatted
import app.grapheneos.camera.ui.gallery.components.formattedTime
import app.grapheneos.camera.ui.gallery.components.localDateTime
import java.time.LocalDateTime
import java.time.ZoneId

private const val DATE_SKELETON = "EEEMMMdyyyy"
private const val COORDINATE_PATTERN = "0.00000"

private val CONTENT_PADDING = 16.dp
private val SECTION_SPACING = 24.dp
private val TITLE_SPACING = 12.dp
private val LOCATION_TOP_PADDING = 8.dp
private val LOCATION_START_PADDING = 20.dp
private val LOCATION_TEXT_SPACING = 20.dp

@Composable
internal fun FileDetails(
    details: CapturedItemDetails,
    isVideo: Boolean,
    uri: String,
    onOpenMapClick: (() -> Unit)?,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = CONTENT_PADDING,
                end = CONTENT_PADDING,
                bottom = SECTION_SPACING,
            ),
    ) {
        (details.created ?: details.modified)?.let { capturedAt ->
            CaptureDateTime(
                capturedAt = capturedAt,
                onCopy = onCopy,
            )
        }
        Text(
            text = stringResource(R.string.gallery_details),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                top = SECTION_SPACING,
                bottom = TITLE_SPACING,
            ),
        )
        FileDetailsCard(
            details = details,
            isVideo = isVideo,
            uri = uri,
            isExpanded = isExpanded,
            onExpandedChange = { isExpanded = it },
            onCopy = onCopy,
        )
        details.location?.let { location ->
            LocationRow(
                location = location,
                onOpenMapClick = onOpenMapClick,
                onCopy = onCopy,
            )
        }
    }
}

@Composable
private fun CaptureDateTime(
    capturedAt: MediaTimestamp,
    onCopy: (String) -> Unit,
) {
    val text = dateTimeText(capturedAt)

    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.copyOnLongClick(
            text = text,
            copyLabel = stringResource(R.string.copy_to_clipboard),
            onCopy = onCopy,
        ),
    )
}

@Composable
internal fun dateTimeText(timestamp: MediaTimestamp): String {
    val locale = LocalLocale.current.platformLocale
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val dateTime = remember(timestamp) { timestamp.localDateTime(ZoneId.systemDefault()) }

    return stringResource(
        R.string.gallery_details_date_time,
        dateTime.formatted(
            locale = locale,
            skeleton = DATE_SKELETON,
        ),
        dateTime.formattedTime(
            locale = locale,
            is24Hour = is24Hour,
        ),
    )
}

@Composable
private fun LocationRow(
    location: GeoCoordinates,
    onOpenMapClick: (() -> Unit)?,
    onCopy: (String) -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val format = remember(locale) { decimalFormat(COORDINATE_PATTERN, locale) }
    val coordinates = stringResource(
        R.string.gallery_details_coordinates,
        format.format(location.latitude),
        format.format(location.longitude),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LOCATION_TOP_PADDING)
            .clip(MaterialTheme.shapes.large)
            .copyOnLongClick(
                text = coordinates,
                copyLabel = stringResource(R.string.copy_to_clipboard),
                onCopy = onCopy,
                onClickLabel = onOpenMapClick?.let {
                    stringResource(R.string.gallery_details_open_in_maps)
                },
                onClick = onOpenMapClick ?: {},
            )
            .minimumInteractiveComponentSize()
            .padding(start = LOCATION_START_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(LOCATION_TEXT_SPACING))
        Text(
            text = coordinates,
            style = MaterialTheme.typography.bodyLarge,
        )
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
                    fileName = "IMG_20231016_120000_000.jpg",
                    relativePath = "DCIM/Camera/",
                    sizeBytes = 3_456_789,
                    resolution = Size(4032, 3024),
                    duration = null,
                    created = MediaTimestamp.WallClock(dateTime),
                    modified = null,
                    camera = CameraParameters(
                        make = "Google",
                        model = "Pixel 8 Pro",
                        fNumber = 1.68,
                        exposureSeconds = 1.0 / 120,
                        focalLengthMillimeters = 6.9,
                        iso = 52,
                    ),
                    location = GeoCoordinates(
                        latitude = 47.5,
                        longitude = -122.25,
                    ),
                ),
                isVideo = false,
                uri = "content://media/external/images/media/1",
                onOpenMapClick = {},
                onCopy = {},
            )
        }
    }
}
