package app.grapheneos.camera.ui.mediaviewer.components.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.components.choicebutton.ChoiceButton
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.mediaviewer.playback.PLAYBACK_SPEEDS
import java.text.NumberFormat

private const val SPEED_FRACTION_DIGITS = 2

private val SHEET_HORIZONTAL_PADDING = 16.dp
private val TITLE_BOTTOM_PADDING = 16.dp
private val SHEET_BOTTOM_PADDING = 24.dp
private val BUTTON_SPACING = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSpeedSheet(
    speed: Float,
    onSpeedSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PlaybackSpeedContent(
            speed = speed,
            onSpeedSelect = onSpeedSelect,
        )
    }
}

@Composable
internal fun playbackSpeedLabel(speed: Float): String {
    val locale = Locale.current
    val format = remember(locale) {
        NumberFormat.getNumberInstance(locale.platformLocale).apply {
            maximumFractionDigits = SPEED_FRACTION_DIGITS
        }
    }

    return stringResource(R.string.playback_speed_value, format.format(speed))
}

@Composable
private fun PlaybackSpeedContent(
    speed: Float,
    onSpeedSelect: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.padding(
            start = SHEET_HORIZONTAL_PADDING,
            end = SHEET_HORIZONTAL_PADDING,
            bottom = SHEET_BOTTOM_PADDING,
        ),
    ) {
        Text(
            text = stringResource(R.string.playback_speed),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = TITLE_BOTTOM_PADDING),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(BUTTON_SPACING),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            PLAYBACK_SPEEDS.forEach { option ->
                ChoiceButton(
                    text = playbackSpeedLabel(option),
                    selected = option == speed,
                    onClick = { onSpeedSelect(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PlaybackSpeedContentPreview() {
    CameraPreviewTheme {
        PlaybackSpeedContent(
            speed = 2f,
            onSpeedSelect = {},
        )
    }
}
