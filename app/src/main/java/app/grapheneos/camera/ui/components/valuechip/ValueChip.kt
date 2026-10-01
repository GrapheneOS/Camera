package app.grapheneos.camera.ui.components.valuechip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.TABULAR_FIGURES

private val CHIP_HEIGHT = 32.dp
private val CHIP_MIN_WIDTH = 52.dp
private val CHIP_PADDING = 12.dp

@Composable
internal fun ValueChip(
    text: String,
    modifier: Modifier = Modifier,
    colors: ValueChipColors = ValueChipColors.fromTheme(),
) {
    Box(
        modifier = modifier
            .heightIn(min = CHIP_HEIGHT)
            .widthIn(min = CHIP_MIN_WIDTH)
            .background(
                color = colors.containerColor,
                shape = CircleShape,
            )
            .padding(horizontal = CHIP_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.contentColor,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            maxLines = 1,
        )
    }
}

@PreviewLightDark
@Composable
private fun ValueChipPreview() {
    CameraPreviewColumn {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            ValueChip(text = "0")
            ValueChip(text = "Auto")
            ValueChip(text = "3968K")
            ValueChip(text = "2.2×")
        }
    }
}
