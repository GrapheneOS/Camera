package app.grapheneos.camera.ui.mediaviewer.components.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.mediaviewer.playback.PLAYBACK_SPEEDS
import java.text.NumberFormat

private const val SPEED_FRACTION_DIGITS = 2

private val SHEET_HORIZONTAL_PADDING = 16.dp
private val TITLE_BOTTOM_PADDING = 16.dp
private val SHEET_BOTTOM_PADDING = 24.dp
private val BUTTON_SPACING = 4.dp
private val BUTTON_HEIGHT = 48.dp
private val BUTTON_CONTENT_PADDING = PaddingValues(horizontal = 4.dp)

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
            modifier = Modifier.fillMaxWidth(),
        ) {
            PLAYBACK_SPEEDS.forEach { option ->
                SpeedButton(
                    label = playbackSpeedLabel(option),
                    isSelected = option == speed,
                    onClick = { onSpeedSelect(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SpeedButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.secondaryContainer
            else -> colors.surfaceContainerHighest
        },
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.onSecondaryContainer
            else -> colors.onSurface
        },
    )
    val restingCornerRadius = MaterialTheme.shapes.medium.topStart.toPx(
        shapeSize = Size.Zero,
        density = density,
    )
    val selectedCornerRadius = with(density) { BUTTON_HEIGHT.toPx() / 2 }
    val cornerRadius by animateFloatAsState(
        targetValue = when {
            isSelected -> selectedCornerRadius
            else -> restingCornerRadius
        },
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .height(BUTTON_HEIGHT)
            .semantics {
                role = Role.RadioButton
                selected = isSelected
            },
        shape = RoundedCornerShape(cornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        contentPadding = BUTTON_CONTENT_PADDING,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
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
