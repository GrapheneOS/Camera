package app.grapheneos.camera.ui.components.modeselector

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.modeselector.gesture.ModeSelectorState

internal val ITEM_PADDING = 24.dp
private val ITEM_HEIGHT = 34.dp

private const val PRESSED_SCALE = 1.05f

@Composable
internal fun ModeSelectorItem(
    label: String,
    index: Int,
    selected: Boolean,
    enabled: Boolean,
    state: ModeSelectorState,
    style: TextStyle,
    highlightContentColor: Color,
    customActions: List<CustomAccessibilityAction>,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale = animateFloatAsState(
        targetValue = when {
            isPressed || isFocused -> PRESSED_SCALE
            else -> 1f
        },
    )

    Box(
        modifier = Modifier
            .height(ITEM_HEIGHT)
            .semantics {
                text = AnnotatedString(label)
                collectionItemInfo = CollectionItemInfo(
                    rowIndex = 0,
                    rowSpan = 1,
                    columnIndex = index,
                    columnSpan = 1,
                )
                this.customActions = customActions
            }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = { state.select(index = index) },
            )
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                recolorOverHighlight(
                    index = index,
                    state = state,
                    color = highlightContentColor,
                )
            }
            .padding(horizontal = ITEM_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clearAndSetSemantics {},
            style = style,
            softWrap = false,
            maxLines = 1,
        )
    }
}

private fun DrawScope.recolorOverHighlight(
    index: Int,
    state: ModeSelectorState,
    color: Color,
) {
    val geometry = state.geometry

    drawRoundRect(
        color = color,
        topLeft = Offset(
            x = geometry.highlightStartIn(
                index = index,
                position = state.position,
                isRtl = layoutDirection == LayoutDirection.Rtl,
            ),
            y = 0f,
        ),
        size = Size(
            width = geometry.highlightWidth(position = state.position),
            height = size.height,
        ),
        cornerRadius = CornerRadius(size.height / 2),
        blendMode = BlendMode.SrcAtop,
    )
}
