package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.highlight.drawHighlight
import app.grapheneos.camera.ui.components.highlight.highlightedContent
import app.grapheneos.camera.ui.components.motion.animateEnabledAlpha
import app.grapheneos.camera.ui.components.pillselector.gesture.PillSelectorSelection
import app.grapheneos.camera.ui.components.pillselector.gesture.pillSelectorInput
import app.grapheneos.camera.ui.components.pillselector.gesture.rememberPillSelectorSelection
import app.grapheneos.camera.ui.components.pillselector.model.PillSelectorSpacing
import app.grapheneos.camera.ui.core.CameraPreviewColumn

private val CONTAINER_PADDING = 2.dp

/**
 * The highlight takes the size of the selected item and slides to the next one. [item] draws an
 * item's content in its own size, in its scope's content color; the part under the highlight is
 * recolored to the selected content color, split along the highlight's edge. Each item is
 * announced as a radio button, with the semantics its content sets. [onItemSelected] reports a
 * tapped item and, when [draggable], the item a lifted horizontal drag leaves the highlight nearest
 * to; a canceled drag reports nothing. Otherwise drags pass to the parent.
 */
@Composable
internal fun PillSelector(
    selectedIndex: Int,
    itemCount: Int,
    onItemSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    draggable: Boolean = false,
    spacing: PillSelectorSpacing = PillSelectorSpacing.Spaced,
    colors: PillSelectorColors = PillSelectorColors.fromTheme(),
    item: @Composable PillSelectorItemScope.(index: Int) -> Unit,
) {
    require(selectedIndex in 0 until itemCount) {
        "selectedIndex must be in 0 until $itemCount, was $selectedIndex"
    }

    val selection = rememberPillSelectorSelection(
        selectedIndex = selectedIndex,
        itemCount = itemCount,
    )

    PillSelectorLayers(
        selectedIndex = selectedIndex,
        itemCount = itemCount,
        onItemSelected = onItemSelected,
        selection = selection,
        enabled = enabled,
        spacing = spacing,
        colors = colors,
        item = item,
        modifier = modifier.pillSelectorInput(
            selection = selection,
            enabled = enabled && draggable && itemCount > 1,
            onItemSelected = onItemSelected,
        ),
    )
}

@Composable
private fun PillSelectorLayers(
    selectedIndex: Int,
    itemCount: Int,
    onItemSelected: (index: Int) -> Unit,
    selection: PillSelectorSelection,
    enabled: Boolean,
    spacing: PillSelectorSpacing,
    colors: PillSelectorColors,
    item: @Composable PillSelectorItemScope.(index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentColors = rememberUpdatedState(colors)
    val alpha by animateEnabledAlpha(enabled = enabled)

    Row(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .background(
                color = colors.containerColor,
                shape = CircleShape,
            )
            .padding(all = CONTAINER_PADDING)
            .selectableGroup()
            .drawBehind {
                drawHighlight(
                    start = selection.geometry.start(selection = selection.value),
                    width = selection.geometry.width(selection = selection.value),
                    color = currentColors.value.selectedContainerColor,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(space = spacing.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(itemCount) { index ->
            val scope = remember(index, selection) {
                PillSelectorItemScope(
                    index = index,
                    selection = selection,
                    colors = currentColors,
                )
            }

            PillSelectorItem(
                selected = index == selectedIndex,
                enabled = enabled,
                onClick = { onItemSelected(index) },
                selection = selection,
                index = index,
                colors = colors,
            ) {
                scope.item(index)
            }
        }
    }
}

@Composable
private fun PillSelectorItem(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    selection: PillSelectorSelection,
    index: Int,
    colors: PillSelectorColors,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .onPlaced { coordinates ->
                selection.geometry.place(
                    index = index,
                    start = coordinates.positionInParent().x,
                    width = coordinates.size.width.toFloat(),
                )
            }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .clip(shape = CircleShape)
                .indication(
                    interactionSource = interactionSource,
                    indication = ripple(color = colors.contentColor),
                ),
        )
        Box(
            modifier = Modifier.highlightedContent(
                highlightColor = colors.selectedContainerColor,
                highlightContentColor = colors.selectedContentColor,
                highlightStart = {
                    selection.geometry.start(selection = selection.value) -
                        selection.geometry.itemStart(index = index)
                },
                highlightWidth = { selection.geometry.width(selection = selection.value) },
            ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@PreviewLightDark
@Composable
private fun PillSelectorPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewTextSelector()
            PreviewCircleSelector(selectedIndex = 0)
            PreviewCircleSelector(
                selectedIndex = 1,
                enabled = false,
            )
        }
    }
}

@Composable
private fun PreviewTextSelector() {
    val labels = listOf("Portrait", "Photo", "Night Sight")
    var currentIndex by remember { mutableIntStateOf(1) }

    PillSelector(
        selectedIndex = currentIndex,
        itemCount = labels.size,
        onItemSelected = { index -> currentIndex = index },
    ) { index ->
        BasicText(
            text = labels[index],
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}

@Composable
private fun PreviewCircleSelector(
    selectedIndex: Int,
    enabled: Boolean = true,
) {
    val labels = listOf("A", "B", "C")
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }

    PillSelector(
        selectedIndex = currentIndex,
        itemCount = labels.size,
        onItemSelected = { index -> currentIndex = index },
        enabled = enabled,
    ) { index ->
        Box(
            modifier = Modifier.size(size = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = labels[index],
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
