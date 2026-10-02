package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import app.grapheneos.camera.ui.components.motion.animateEnabledAlpha
import app.grapheneos.camera.ui.core.CameraPreviewColumn

private val ITEM_SPACING = 4.dp
private val CONTAINER_PADDING = 2.dp
private val SELECTION_SPEC = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 500f,
)

/**
 * The highlight takes the size of the selected item and slides to the next one. [item] draws an
 * item's content in its own size; its scope gives the content color, which crossfades with the
 * highlight. Each item is announced as a radio button, with the semantics its content sets.
 */
@Composable
internal fun PillSelector(
    selectedIndex: Int,
    itemCount: Int,
    onItemClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: PillSelectorColors = PillSelectorColors.fromTheme(),
    item: @Composable PillSelectorItemScope.(index: Int) -> Unit,
) {
    require(selectedIndex in 0 until itemCount) {
        "selectedIndex must be in 0 until $itemCount, was $selectedIndex"
    }

    val selection = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = SELECTION_SPEC,
    )
    val currentColors = rememberUpdatedState(colors)
    val geometry = remember(itemCount) { PillSelectorGeometry(itemCount = itemCount) }
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
                drawPillSelectorHighlight(
                    selection = selection.value,
                    geometry = geometry,
                    color = currentColors.value.selectedContainerColor,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(space = ITEM_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(itemCount) { index ->
            val scope = remember(index) {
                PillSelectorItemScope(
                    index = index,
                    selection = selection,
                    colors = currentColors,
                )
            }

            PillSelectorItem(
                selected = index == selectedIndex,
                enabled = enabled,
                onClick = { onItemClick(index) },
                geometry = geometry,
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
    geometry: PillSelectorGeometry,
    index: Int,
    colors: PillSelectorColors,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .onPlaced { coordinates ->
                geometry.place(
                    index = index,
                    start = coordinates.positionInParent().x,
                    width = coordinates.size.width.toFloat(),
                )
            }
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = ripple(
                    color = when {
                        selected -> colors.selectedContentColor
                        else -> colors.contentColor
                    },
                ),
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
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
        onItemClick = { index -> currentIndex = index },
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
        onItemClick = { index -> currentIndex = index },
        enabled = enabled,
    ) { index ->
        Box(
            modifier = Modifier.size(48.dp),
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
