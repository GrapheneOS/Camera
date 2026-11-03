package app.grapheneos.camera.ui.components.choicebutton

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.grapheneos.camera.ui.components.motion.LocalContentRotation
import app.grapheneos.camera.ui.components.motion.MORPH_SPEC
import app.grapheneos.camera.ui.components.motion.QUICK_COLOR_SPEC
import app.grapheneos.camera.ui.components.motion.animateEnabledAlpha
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn
import app.grapheneos.camera.ui.core.preview.PREVIEW_MIC_ICON
import app.grapheneos.camera.ui.core.preview.PREVIEW_MIC_OFF_ICON

internal val CHOICE_BUTTON_SIZE = 48.dp
private val RESTING_CORNER_RADIUS = 12.dp
private val CONTENT_PADDING = 4.dp
private val RESTING_SHAPE = RoundedCornerShape(size = RESTING_CORNER_RADIUS)

@Composable
internal fun ChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ChoiceButtonColors = ChoiceButtonColors.fromSurfaceTheme(),
    interactionSource: MutableInteractionSource? = null,
) {
    ChoiceButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ChoiceButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ChoiceButtonColors = ChoiceButtonColors.fromSurfaceTheme(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val alpha by animateEnabledAlpha(enabled = enabled)
    val roundness by animateFloatAsState(
        targetValue = when {
            selected -> 1f
            else -> 0f
        },
        animationSpec = MORPH_SPEC,
    )
    val containerColor by animateColorAsState(
        targetValue = colors.containerColor(selected),
        animationSpec = QUICK_COLOR_SPEC,
    )
    val restingRadius = with(LocalDensity.current) { RESTING_CORNER_RADIUS.toPx() }
    val rippleShape = when {
        selected -> CircleShape
        else -> RESTING_SHAPE
    }

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = CHOICE_BUTTON_SIZE,
                minHeight = CHOICE_BUTTON_SIZE,
            )
            .drawBehind {
                drawRoundRect(
                    color = containerColor,
                    cornerRadius = CornerRadius(
                        lerp(restingRadius, size.minDimension / 2, roundness),
                    ),
                )
            }
            .selectable(
                selected = selected,
                interactionSource = interactions,
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
                .clip(shape = rippleShape)
                .indication(
                    interactionSource = interactions,
                    indication = ripple(color = colors.contentColor(selected)),
                ),
        )
        ChoiceButtonContent(
            selected = selected,
            colors = colors,
            alpha = { alpha },
            content = content,
        )
    }
}

@Composable
private fun ChoiceButtonContent(
    selected: Boolean,
    colors: ChoiceButtonColors,
    alpha: () -> Float,
    content: @Composable BoxScope.() -> Unit,
) {
    val rotation = LocalContentRotation.current
    val contentColor by animateColorAsState(
        targetValue = colors.contentColor(selected),
        animationSpec = QUICK_COLOR_SPEC,
    )

    Box(
        modifier = Modifier
            .padding(horizontal = CONTENT_PADDING)
            .graphicsLayer {
                rotationZ = rotation()
                this.alpha = alpha()
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    color = contentColor,
                    blendMode = BlendMode.SrcIn,
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun ChoiceButtonPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewIconChoices(enabled = true)
            PreviewIconChoices(enabled = false)
            PreviewTextChoices()
        }
    }
}

@Composable
private fun PreviewIconChoices(
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        listOf(PREVIEW_MIC_ICON, PREVIEW_MIC_OFF_ICON).forEachIndexed { index, icon ->
            ChoiceButton(
                selected = index == 0,
                onClick = {},
                enabled = enabled,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun PreviewTextChoices() {
    Row(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        listOf("0.5×", "1×", "2×").forEach { label ->
            ChoiceButton(
                text = label,
                selected = label == "1×",
                onClick = {},
            )
        }
    }
}
