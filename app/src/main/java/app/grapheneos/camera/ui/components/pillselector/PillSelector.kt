package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.highlight.drawHighlight
import app.grapheneos.camera.ui.components.highlight.highlightedContent
import app.grapheneos.camera.ui.components.motion.EXPAND_HORIZONTALLY
import app.grapheneos.camera.ui.components.motion.SHRINK_HORIZONTALLY
import app.grapheneos.camera.ui.components.motion.animateEnabledAlpha
import app.grapheneos.camera.ui.components.pillselector.gesture.PillSelectorSelection
import app.grapheneos.camera.ui.components.pillselector.gesture.pillSelectorInput
import app.grapheneos.camera.ui.components.pillselector.gesture.rememberPillSelectorSelection
import app.grapheneos.camera.ui.components.pillselector.model.PillSelectorSpacing
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn

private val CONTAINER_PADDING = 2.dp

/**
 * The highlight takes the size of the selected item and slides to the next one. [item] draws an
 * item's content in its own size, in its scope's content color; the part under the highlight is
 * recolored to the selected content color, split along the highlight's edge. Each item is
 * announced as a radio button, with the semantics its content sets. [onItemSelected] reports a
 * tapped item and, when [draggable], the item a lifted horizontal drag leaves the highlight nearest
 * to; a canceled drag reports nothing. Otherwise drags pass to the parent.
 *
 * [items] are told apart by [key] when they change: an item with a new key grows in, one whose key
 * is gone shrinks away, and an item that keeps its key stays in its slot and is drawn with its new
 * value. The highlight stays on the item it is on while the others make room, and slides when
 * [selected] changes.
 */
@Composable
internal fun <Item, Key> PillSelector(
    items: List<Item>,
    selected: Item,
    key: (item: Item) -> Key,
    onItemSelected: (item: Item) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    draggable: Boolean = false,
    spacing: PillSelectorSpacing = PillSelectorSpacing.Spaced,
    colors: PillSelectorColors = PillSelectorColors.fromTheme(),
    item: @Composable PillSelectorItemScope.(item: Item) -> Unit,
) {
    val entries = rememberPillSelectorEntries(
        items = items,
        key = key,
    )
    val selectedIndex = entries.keys.indexOf(key(selected))
    require(selectedIndex >= 0 && selected in items) {
        "selected must be one of items, was $selected"
    }

    val selection = rememberPillSelectorSelection(
        selectedIndex = selectedIndex,
        keys = entries.keys,
    )
    val currentOnItemSelected = rememberUpdatedState(onItemSelected)
    val onIndexSelected: (index: Int) -> Unit = remember(entries) {
        { index ->
            val entry = entries.renderedEntries.getOrNull(index)
            if (entry != null && entry.isShown) {
                currentOnItemSelected.value(entry.item)
            }
        }
    }

    PillSelectorLayers(
        entries = entries.renderedEntries,
        selectedIndex = selectedIndex,
        onIndexSelected = onIndexSelected,
        selection = selection,
        enabled = enabled,
        spacing = spacing,
        colors = colors,
        item = item,
        modifier = modifier.pillSelectorInput(
            selection = selection,
            enabled = enabled && draggable && entries.renderedEntries.size > 1,
            onItemSelected = onIndexSelected,
        ),
    )
}

@Composable
private fun <Item> PillSelectorLayers(
    entries: List<PillSelectorEntry<Item, *>>,
    selectedIndex: Int,
    onIndexSelected: (index: Int) -> Unit,
    selection: PillSelectorSelection<*>,
    enabled: Boolean,
    spacing: PillSelectorSpacing,
    colors: PillSelectorColors,
    item: @Composable PillSelectorItemScope.(item: Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentColors = rememberUpdatedState(colors)
    val alpha by animateEnabledAlpha(enabled = enabled)

    val halfGap = spacing.gap / 2
    // Two separately rounded half gaps can differ from Row's full gap by a pixel.
    val remainingGap = with(LocalDensity.current) {
        (spacing.gap.roundToPx() - halfGap.roundToPx()).toDp()
    }
    val edgePadding = PaddingValues(horizontal = halfGap)
    val itemPadding = PaddingValues(
        start = halfGap,
        end = remainingGap,
    )

    Row(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .background(
                color = colors.containerColor,
                shape = CircleShape,
            )
            .padding(
                horizontal = CONTAINER_PADDING - halfGap,
                vertical = CONTAINER_PADDING,
            )
            .selectableGroup()
            .drawBehind {
                drawHighlight(
                    start = selection.geometry.start(selection = selection.value),
                    width = selection.geometry.width(selection = selection.value),
                    color = currentColors.value.selectedContainerColor,
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEachIndexed { index, entry ->
            key(entry.key) {
                PillSelectorEntryItem(
                    entry = entry,
                    index = index,
                    selected = index == selectedIndex,
                    onIndexSelected = onIndexSelected,
                    selection = selection,
                    enabled = enabled,
                    padding = when (index) {
                        entries.lastIndex -> edgePadding
                        else -> itemPadding
                    },
                    colors = colors,
                    currentColors = currentColors,
                    item = item,
                )
            }
        }
    }
}

@Composable
private fun <Item> RowScope.PillSelectorEntryItem(
    entry: PillSelectorEntry<Item, *>,
    index: Int,
    selected: Boolean,
    onIndexSelected: (index: Int) -> Unit,
    selection: PillSelectorSelection<*>,
    enabled: Boolean,
    padding: PaddingValues,
    colors: PillSelectorColors,
    currentColors: State<PillSelectorColors>,
    item: @Composable PillSelectorItemScope.(item: Item) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val leftPadding = with(density) { padding.calculateLeftPadding(layoutDirection).roundToPx() }
    val rightPadding = with(density) { padding.calculateRightPadding(layoutDirection).roundToPx() }
    var contentWidth by remember { mutableIntStateOf(0) }
    val scope = remember(index, selection) {
        PillSelectorItemScope(
            index = index,
            selection = selection,
            colors = currentColors,
        )
    }

    AnimatedVisibility(
        visibleState = entry.visibility,
        modifier = Modifier.onPlaced { coordinates ->
            val start = coordinates.positionInParent().x
            val width = coordinates.size.width
            val contentOffset = Alignment.CenterHorizontally.align(
                size = contentWidth,
                space = width,
                layoutDirection = layoutDirection,
            )

            selection.geometry.place(
                index = index,
                start = start + leftPadding,
                width = (width - leftPadding - rightPadding).coerceAtLeast(0).toFloat(),
                contentStart = start + contentOffset + leftPadding,
            )
        },
        enter = EXPAND_HORIZONTALLY,
        exit = SHRINK_HORIZONTALLY,
    ) {
        PillSelectorItem(
            selected = selected,
            enabled = enabled && entry.isShown,
            onClick = { onIndexSelected(index) },
            selection = selection,
            index = index,
            colors = colors,
            modifier = Modifier
                .onSizeChanged { size -> contentWidth = size.width }
                .padding(padding),
        ) {
            scope.item(entry.item)
        }
    }
}

@Composable
private fun PillSelectorItem(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    selection: PillSelectorSelection<*>,
    index: Int,
    colors: PillSelectorColors,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.selectable(
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
                        selection.geometry.contentStart(index = index)
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
        items = labels,
        key = { it },
        selected = labels[currentIndex],
        onItemSelected = { label -> currentIndex = labels.indexOf(label) },
    ) { label ->
        BasicText(
            text = label,
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
        items = labels,
        key = { it },
        selected = labels[currentIndex],
        onItemSelected = { label -> currentIndex = labels.indexOf(label) },
        enabled = enabled,
    ) { label ->
        Box(
            modifier = Modifier.size(size = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
