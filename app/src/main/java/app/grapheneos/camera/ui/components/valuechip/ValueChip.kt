package app.grapheneos.camera.ui.components.valuechip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.LocalContentRotation
import app.grapheneos.camera.ui.components.motion.rotateLayout
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.TABULAR_FIGURES

private val CHIP_HEIGHT = 32.dp
private val CHIP_MIN_WIDTH = 52.dp
private val CHIP_PADDING = 12.dp
private val LABEL_SIZE = 14.dp
private val LABEL_LINE_HEIGHT = 20.dp

@Composable
internal fun ValueChip(
    text: String,
    modifier: Modifier = Modifier,
    colors: ValueChipColors = ValueChipColors.fromTheme(),
) {
    ValueChip(
        modifier = modifier,
        colors = colors,
    ) { textStyle ->
        Text(
            text = text,
            style = textStyle,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ValueChip(
    modifier: Modifier = Modifier,
    colors: ValueChipColors = ValueChipColors.fromTheme(),
    content: @Composable RowScope.(textStyle: TextStyle) -> Unit,
) {
    val density = LocalDensity.current
    val textStyle = MaterialTheme.typography.labelLarge.copy(
        color = colors.contentColor,
        fontSize = with(density) { LABEL_SIZE.toSp() },
        lineHeight = with(density) { LABEL_LINE_HEIGHT.toSp() },
        fontFeatureSettings = TABULAR_FIGURES,
    )

    Row(
        modifier = modifier
            .rotateLayout(rotation = LocalContentRotation.current)
            .heightIn(min = CHIP_HEIGHT)
            .widthIn(min = CHIP_MIN_WIDTH)
            .background(
                color = colors.containerColor,
                shape = CircleShape,
            )
            .padding(horizontal = CHIP_PADDING)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content(textStyle)
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
