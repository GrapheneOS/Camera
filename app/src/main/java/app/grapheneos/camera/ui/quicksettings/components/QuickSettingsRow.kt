package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.components.choicebutton.ChoiceButton
import app.grapheneos.camera.ui.quicksettings.screen.model.OptionLimit
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickChoiceUiModel

@Composable
internal fun <T> QuickSettingsRow(
    label: String,
    setting: QuickChoiceUiModel<T>,
    onSelect: (T) -> Unit,
    onLimitClick: (OptionLimit) -> Unit,
    valueLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optionLabel: @Composable (T) -> String = valueLabel,
    icon: ((T) -> ImageVector)? = null,
    highlight: () -> Float = { 0f },
) {
    QuickSettingsRowLayout(
        label = label,
        value = valueLabel(setting.selected),
        modifier = modifier,
        highlight = highlight,
        onUnavailableClick = setting.limit?.let { limit -> { onLimitClick(limit) } },
        enabled = enabled,
    ) {
        setting.options.forEach { option ->
            QuickSettingsOption(
                rowLabel = label,
                text = optionLabel(option),
                icon = icon?.invoke(option),
                selected = option == setting.selected,
                onClick = { onSelect(option) },
                enabled = enabled && option !in setting.unavailable,
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
    enabled: Boolean = true,
    offIcon: ImageVector? = null,
    onIcon: ImageVector? = null,
    highlight: () -> Float = { 0f },
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
        highlight = highlight,
    ) {
        QuickSettingsOption(
            rowLabel = label,
            text = off,
            icon = offIcon,
            selected = !checked,
            onClick = { onToggle(false) },
            enabled = enabled,
        )
        QuickSettingsOption(
            rowLabel = label,
            text = on,
            icon = onIcon,
            selected = checked,
            onClick = { onToggle(true) },
            enabled = enabled,
        )
    }
}

@Composable
private fun QuickSettingsOption(
    rowLabel: String,
    text: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val description = stringResource(R.string.quick_settings_option_description, rowLabel, text)

    ChoiceButton(
        selected = selected,
        onClick = {
            if (!selected) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            onClick()
        },
        modifier = Modifier.semantics { contentDescription = description },
        enabled = enabled,
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
