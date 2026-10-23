package app.grapheneos.camera.ui.gallery.components.bars

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.gallery.components.formatted
import app.grapheneos.camera.ui.gallery.components.formattedTime
import java.time.LocalDate
import java.time.LocalDateTime

private const val DATE_SKELETON = "MMMd"
private const val DATE_WITH_YEAR_SKELETON = "yMMMd"

private val TITLE_HORIZONTAL_PADDING = 16.dp

@Composable
internal fun CaptureTimeTitle(
    capturedAt: LocalDateTime,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val date = remember(capturedAt, locale) {
        val skeleton = when (capturedAt.year) {
            LocalDate.now().year -> DATE_SKELETON
            else -> DATE_WITH_YEAR_SKELETON
        }

        capturedAt.formatted(
            locale = locale,
            skeleton = skeleton,
        )
    }
    val time = remember(capturedAt, locale, is24Hour) {
        capturedAt.formattedTime(
            locale = locale,
            is24Hour = is24Hour,
        )
    }
    val clickModifier = when (onClick) {
        null -> Modifier
        else -> Modifier.clickable(
            onClickLabel = stringResource(R.string.show_in_gallery),
            role = Role.Button,
            onClick = onClick,
        )
    }

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .then(clickModifier)
            .padding(horizontal = TITLE_HORIZONTAL_PADDING)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@PreviewLightDark
@Composable
private fun CaptureTimeTitlePreview() {
    CameraPreviewTheme {
        CaptureTimeTitle(
            capturedAt = LocalDateTime.of(2023, 10, 17, 17, 45),
            onClick = {},
        )
    }
}
