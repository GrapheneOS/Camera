package app.grapheneos.camera.ui.components.modeselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.highlight.drawHighlight
import app.grapheneos.camera.ui.components.modeselector.gesture.ModeSelectorEffects
import app.grapheneos.camera.ui.components.modeselector.gesture.ModeSelectorState
import app.grapheneos.camera.ui.components.modeselector.gesture.modeSelectorInput
import app.grapheneos.camera.ui.components.modeselector.gesture.rememberModeSelectorState
import app.grapheneos.camera.ui.components.motion.animateEnabledAlpha
import app.grapheneos.camera.ui.core.CameraPreviewColumn

/**
 * [onModeSelected] is called once the strip has settled on another mode after a release or a tap;
 * the caller sets [selectedIndex] to it at once, since the strip animates to any other value.
 * [ModeSelectorState.settleNow] finishes a settle at once, for a capture, and so does leaving
 * composition. [labels] and [selectedIndex] change together: new [labels] drop a pending settle and
 * show [selectedIndex] at once. While [switching], touches are ignored without the dimming of
 * [enabled].
 */
@Composable
internal fun ModeSelector(
    labels: List<String>,
    selectedIndex: Int,
    onModeSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    switching: Boolean = false,
    state: ModeSelectorState = rememberModeSelectorState(initialIndex = selectedIndex),
    colors: ModeSelectorColors = ModeSelectorColors.fromTheme(),
) {
    require(selectedIndex in labels.indices) {
        "selectedIndex must be in ${labels.indices}, was $selectedIndex"
    }

    val acceptsInput = enabled && !switching
    val alpha by animateEnabledAlpha(enabled = enabled)

    ModeSelectorEffects(
        state = state,
        labels = labels,
        selectedIndex = selectedIndex,
        onModeSelected = onModeSelected,
    )
    ModeSelectorLayers(
        labels = labels,
        selectedIndex = selectedIndex,
        state = state,
        enabled = acceptsInput,
        colors = colors,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .modeSelectorInput(
                state = state,
                enabled = acceptsInput,
                layoutDirection = LocalLayoutDirection.current,
            ),
    )
}

@Composable
private fun ModeSelectorLayers(
    labels: List<String>,
    selectedIndex: Int,
    state: ModeSelectorState,
    enabled: Boolean,
    colors: ModeSelectorColors,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.labelLarge.copy(color = colors.contentColor)
    val itemActions = remember(labels, state, enabled, selectedIndex) {
        val selectActions = labels.mapIndexed { index, label ->
            CustomAccessibilityAction(label = label) {
                state.select(index = index)
                true
            }
        }

        labels.indices.map { index ->
            when {
                enabled -> selectActions.filterIndexed { other, _ ->
                    other != index && other != selectedIndex
                }

                else -> emptyList()
            }
        }
    }

    Layout(
        content = {
            labels.forEachIndexed { index, label ->
                ModeSelectorItem(
                    label = label,
                    index = index,
                    selected = index == selectedIndex,
                    enabled = enabled,
                    state = state,
                    style = style,
                    highlightColor = colors.selectedContainerColor,
                    highlightContentColor = colors.selectedContentColor,
                    customActions = itemActions[index],
                )
            }
        },
        modifier = modifier
            .clipToBounds()
            .semantics {
                collectionInfo = CollectionInfo(
                    rowCount = 1,
                    columnCount = labels.size,
                )
            }
            .drawBehind {
                val width = state.geometry.highlightWidth(position = state.position)

                drawHighlight(
                    start = (size.width - width) / 2,
                    width = width,
                    color = colors.selectedContainerColor,
                )
            },
        measurePolicy = rememberModeSelectorMeasurePolicy(state = state),
    )
}

@PreviewLightDark
@Composable
private fun ModeSelectorPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewModeSelector(selectedIndex = 0)
            PreviewModeSelector(selectedIndex = 2)
            PreviewModeSelector(
                selectedIndex = 2,
                enabled = false,
            )
        }
    }
}

@Composable
private fun PreviewModeSelector(
    selectedIndex: Int,
    labels: List<String> = listOf("Long Exposure", "Portrait", "Photo", "Night Sight", "Panorama"),
    enabled: Boolean = true,
) {
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }

    ModeSelector(
        labels = labels,
        selectedIndex = currentIndex,
        onModeSelected = { index -> currentIndex = index },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    )
}
