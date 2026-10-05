package app.grapheneos.camera.ui.components.segmentedicontoggle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.LocalContentRotation
import app.grapheneos.camera.ui.components.pillselector.PillSelector
import app.grapheneos.camera.ui.components.pillselector.PillSelectorColors
import app.grapheneos.camera.ui.components.pillselector.model.PillSelectorSpacing
import app.grapheneos.camera.ui.components.segmentedicontoggle.model.SegmentedIconToggleOption
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_PHOTO_CAMERA_ICON
import app.grapheneos.camera.ui.core.PREVIEW_VIDEOCAM_ICON

private val OPTION_SIZE = 48.dp
private val ICON_SIZE = 24.dp

/**
 * [onOptionSelected] reports a tapped option, or the one a lifted drag leaves the highlight nearest
 * to, even if it is the selected one; [selectedIndex] changes only when the caller sets it, and the
 * highlight slides to it. Each option is announced by its content description.
 */
@Composable
internal fun SegmentedIconToggle(
    options: List<SegmentedIconToggleOption>,
    selectedIndex: Int,
    onOptionSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: PillSelectorColors = PillSelectorColors.fromSurfaceTheme(),
) {
    PillSelector(
        selectedIndex = selectedIndex,
        itemCount = options.size,
        onItemSelected = onOptionSelected,
        modifier = modifier,
        enabled = enabled,
        draggable = true,
        spacing = PillSelectorSpacing.Joined,
        colors = colors,
    ) { index ->
        SegmentedIconToggleIcon(
            option = options[index],
            color = contentColor,
        )
    }
}

@Composable
private fun SegmentedIconToggleIcon(
    option: SegmentedIconToggleOption,
    color: ColorProducer,
) {
    val rotation = LocalContentRotation.current
    val painter = rememberVectorPainter(image = option.icon)

    Box(
        modifier = Modifier
            .size(OPTION_SIZE)
            .semantics { contentDescription = option.contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            modifier = Modifier
                .size(ICON_SIZE)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    rotationZ = rotation()
                }
                .drawBehind {
                    with(painter) { draw(size = size) }
                    drawRect(
                        color = color(),
                        blendMode = BlendMode.SrcIn,
                    )
                },
        )
    }
}

@PreviewLightDark
@Composable
private fun SegmentedIconTogglePreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewSegmentedIconToggle(selectedIndex = 0)
            PreviewSegmentedIconToggle(selectedIndex = 1)
            PreviewSegmentedIconToggle(
                selectedIndex = 0,
                enabled = false,
            )
        }
    }
}

@Composable
private fun PreviewSegmentedIconToggle(
    selectedIndex: Int,
    enabled: Boolean = true,
) {
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }

    SegmentedIconToggle(
        options = listOf(
            SegmentedIconToggleOption(
                icon = PREVIEW_PHOTO_CAMERA_ICON,
                contentDescription = "Photo",
            ),
            SegmentedIconToggleOption(
                icon = PREVIEW_VIDEOCAM_ICON,
                contentDescription = "Video",
            ),
        ),
        selectedIndex = currentIndex,
        onOptionSelected = { index -> currentIndex = index },
        enabled = enabled,
    )
}
