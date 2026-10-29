package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.components.choicebutton.ChoiceButton
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickChoiceUiModel

private val ROW_MIN_HEIGHT = 56.dp
private val ROW_HORIZONTAL_PADDING = 16.dp
private val ROW_VERTICAL_PADDING = 4.dp
private val LABEL_SPACING = 16.dp
private val OPTION_SPACING = 8.dp

@Composable
internal fun <T> QuickSettingsRow(
    label: String,
    setting: QuickChoiceUiModel<T>,
    onSelect: (T) -> Unit,
    valueLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    optionLabel: @Composable (T) -> String = valueLabel,
    icon: ((T) -> ImageVector)? = null,
) {
    QuickSettingsRowLayout(
        label = label,
        value = valueLabel(setting.selected),
        modifier = modifier,
    ) {
        setting.options.forEach { option ->
            QuickSettingsOption(
                rowLabel = label,
                text = optionLabel(option),
                icon = icon?.invoke(option),
                selected = option == setting.selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
internal fun QuickSettingsRow(
    label: String,
    checked: Boolean,
    onToggle: (enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    offIcon: ImageVector? = null,
    onIcon: ImageVector? = null,
) {
    val off = stringResource(R.string.quick_settings_off)
    val on = stringResource(R.string.quick_settings_on)

    QuickSettingsRowLayout(
        label = label,
        value = when {
            checked -> on
            else -> off
        },
        modifier = modifier,
    ) {
        QuickSettingsOption(
            rowLabel = label,
            text = off,
            icon = offIcon,
            selected = !checked,
            onClick = { onToggle(false) },
        )
        QuickSettingsOption(
            rowLabel = label,
            text = on,
            icon = onIcon,
            selected = checked,
            onClick = { onToggle(true) },
        )
    }
}

@Composable
private fun QuickSettingsRowLayout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    options: @Composable RowScope.() -> Unit,
) {
    Layout(
        contents = listOf(
            {
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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
            .padding(
                horizontal = ROW_HORIZONTAL_PADDING,
                vertical = ROW_VERTICAL_PADDING,
            ),
        measurePolicy = QuickSettingsRowMeasurePolicy,
    )
}

@Composable
private fun QuickSettingsOption(
    rowLabel: String,
    text: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.quick_settings_option_description, rowLabel, text)

    ChoiceButton(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        when (icon) {
            null -> Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )

            else -> Icon(
                imageVector = icon,
                contentDescription = null,
            )
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
