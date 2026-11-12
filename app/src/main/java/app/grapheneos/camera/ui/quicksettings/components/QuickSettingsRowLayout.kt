package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R

private val ROW_MIN_HEIGHT = 56.dp
private val ROW_HORIZONTAL_PADDING = 16.dp
private val ROW_VERTICAL_PADDING = 4.dp
private val LABEL_SPACING = 16.dp
private val LABEL_RIPPLE_HORIZONTAL_PADDING = 8.dp
private val LABEL_RIPPLE_VERTICAL_PADDING = 4.dp
private val OPTION_SPACING = 8.dp
private val UNAVAILABLE_ICON_SPACING = 4.dp
private val UNAVAILABLE_ICON_SIZE = 18.dp

@Composable
internal fun QuickSettingsRowLayout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: () -> Float = { 0f },
    onUnavailableClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    options: @Composable RowScope.() -> Unit,
) {
    val highlightColor = MaterialTheme.colorScheme.secondaryContainer

    Layout(
        contents = listOf(
            {
                QuickSettingsRowLabel(
                    label = label,
                    value = value,
                    onUnavailableClick = onUnavailableClick,
                    enabled = enabled,
                )
            },
            {
                Row(
                    modifier = Modifier
                        .horizontalScroll(state = rememberScrollState())
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(space = OPTION_SPACING),
                    content = options,
                )
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .drawBehind {
                drawRect(
                    color = highlightColor,
                    alpha = highlight(),
                )
            }
            .padding(
                horizontal = ROW_HORIZONTAL_PADDING,
                vertical = ROW_VERTICAL_PADDING,
            ),
        measurePolicy = QuickSettingsRowMeasurePolicy,
    )
}

@Composable
private fun QuickSettingsRowLabel(
    label: String,
    value: String,
    onUnavailableClick: (() -> Unit)?,
    enabled: Boolean,
) {
    val clickModifier = when (onUnavailableClick) {
        null -> Modifier
        else -> Modifier.clickable(
            enabled = enabled,
            onClickLabel = stringResource(R.string.quick_settings_why_unavailable),
            role = Role.Button,
            onClick = onUnavailableClick,
        )
    }

    Column(
        modifier = Modifier
            .offset(x = -LABEL_RIPPLE_HORIZONTAL_PADDING)
            .clip(shape = MaterialTheme.shapes.small)
            .then(clickModifier)
            .padding(
                horizontal = LABEL_RIPPLE_HORIZONTAL_PADDING,
                vertical = LABEL_RIPPLE_VERTICAL_PADDING,
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = UNAVAILABLE_ICON_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Box(modifier = Modifier.size(size = UNAVAILABLE_ICON_SIZE)) {
                if (onUnavailableClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private object QuickSettingsRowMeasurePolicy : MultiContentMeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<List<Measurable>>,
        constraints: Constraints,
    ): MeasureResult {
        val (label, options) = measurables.map { it.single() }
        val width = constraints.maxWidth
        val labelSpacing = LABEL_SPACING.roundToPx()
        val labelMinWidth = label.minIntrinsicWidth(height = constraints.maxHeight)
        val optionsPlaceable = options.measure(
            Constraints(maxWidth = (width - labelSpacing - labelMinWidth).coerceAtLeast(0)),
        )
        val labelPlaceable = label.measure(
            Constraints(maxWidth = width - labelSpacing - optionsPlaceable.width),
        )
        val height = maxOf(labelPlaceable.height, optionsPlaceable.height)
            .coerceAtLeast(constraints.minHeight)

        return layout(width = width, height = height) {
            labelPlaceable.place(
                x = 0,
                y = (height - labelPlaceable.height) / 2,
            )
            optionsPlaceable.place(
                x = width - optionsPlaceable.width,
                y = (height - optionsPlaceable.height) / 2,
            )
        }
    }
}
