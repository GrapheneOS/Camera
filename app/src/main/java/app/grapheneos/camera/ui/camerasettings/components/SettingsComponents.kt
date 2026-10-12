package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.CameraPreviewColumn

private const val DISABLED_ALPHA = 0.38f
private const val CHANGED_MARK_ID = "changed_mark"

private val CHANGED_MARK_SIZE = 6.dp
private val CHANGED_MARK_SPACING = 4.dp
private val TRAILING_SPACING = 16.dp

@Composable
internal fun SettingsCategoryHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = 24.dp,
                bottom = 8.dp,
            )
            .semantics { heading() },
    )
}

@Composable
internal fun SettingsClickableItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    label: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isChanged: Boolean = false,
    shape: Shape = MaterialTheme.shapes.small,
    trailing: @Composable (() -> Unit)? = null,
) {
    SettingsItemLayout(
        title = title,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        summary = summary,
        label = label,
        icon = icon,
        enabled = enabled,
        isChanged = isChanged,
        trailing = trailing,
    )
}

@Composable
internal fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    label: String? = null,
    onLabelClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isChanged: Boolean = false,
    shape: Shape = MaterialTheme.shapes.small,
) {
    SettingsItemLayout(
        title = title,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        summary = summary,
        label = label,
        onLabelClick = onLabelClick,
        icon = icon,
        enabled = enabled,
        isChanged = isChanged,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
    )
}

@Composable
private fun SettingsItemLayout(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    label: String? = null,
    onLabelClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isChanged: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ITEM_MIN_HEIGHT)
            .padding(
                start = ITEM_START_PADDING,
                top = ITEM_VERTICAL_PADDING,
                end = ITEM_END_PADDING,
                bottom = ITEM_VERTICAL_PADDING,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor(
                    enabled = enabled,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(modifier = Modifier.width(ITEM_ICON_SPACING))
        }
        Column(modifier = Modifier.weight(1f)) {
            SettingsItemTitle(
                title = title,
                enabled = enabled,
                isChanged = isChanged,
            )
            if (!summary.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                SettingsItemSummary(
                    summary = summary,
                    enabled = enabled,
                )
            }
            if (!label.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsItemLabel(
                    label = label,
                    onLabelClick = onLabelClick,
                    enabled = enabled,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(TRAILING_SPACING))
            trailing()
        }
    }
}

@Composable
private fun SettingsItemTitle(
    title: String,
    enabled: Boolean,
    isChanged: Boolean,
) {
    val changedDescription = stringResource(R.string.camera_settings_changed)
    val markColor = contentColor(
        enabled = enabled,
        color = MaterialTheme.colorScheme.primary,
    )
    val text = buildAnnotatedString {
        append(title)
        if (isChanged) {
            appendInlineContent(CHANGED_MARK_ID)
        }
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = contentColor(
            enabled = enabled,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        inlineContent = mapOf(CHANGED_MARK_ID to changedMark(markColor)),
        modifier = Modifier.semantics {
            if (isChanged) {
                contentDescription = "$title, $changedDescription"
            }
        },
    )
}

@Composable
private fun SettingsItemSummary(
    summary: String,
    enabled: Boolean,
) {
    Text(
        text = summary,
        style = MaterialTheme.typography.bodyMedium,
        color = contentColor(
            enabled = enabled,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun SettingsItemLabel(
    label: String,
    onLabelClick: (() -> Unit)?,
    enabled: Boolean,
) {
    Text(
        text = when (onLabelClick) {
            null -> AnnotatedString(label)
            else -> linkedText(
                text = label,
                link = label,
                onClick = onLabelClick,
            )
        },
        style = MaterialTheme.typography.labelMedium,
        color = contentColor(
            enabled = enabled,
            color = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun changedMark(color: Color): InlineTextContent {
    val placeholder = with(LocalDensity.current) {
        Placeholder(
            width = (CHANGED_MARK_SPACING + CHANGED_MARK_SIZE).toSp(),
            height = CHANGED_MARK_SIZE.toSp(),
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        )
    }

    return InlineTextContent(placeholder) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(CHANGED_MARK_SIZE)
                    .background(
                        color = color,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun contentColor(
    enabled: Boolean,
    color: Color,
): Color {
    return when {
        enabled -> color
        else -> color.copy(alpha = DISABLED_ALPHA)
    }
}

@PreviewLightDark
@Composable
private fun SettingsCategoryHeaderPreview() {
    CameraPreviewColumn {
        SettingsCategoryHeader(
            title = "Composition",
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsClickableItemPreview() {
    CameraPreviewColumn {
        Column(verticalArrangement = Arrangement.spacedBy(GROUPED_ITEM_SPACING)) {
            SettingsClickableItem(
                title = "Image quality",
                summary = "High · 95%",
                label = "Photo only",
                icon = Icons.Outlined.HighQuality,
                shape = groupedItemShape(index = 0, count = 3),
                onClick = {},
            )
            SettingsClickableItem(
                title = "Storage location",
                summary = "Main storage/DCIM/Camera",
                icon = Icons.Outlined.Folder,
                isChanged = true,
                shape = groupedItemShape(index = 1, count = 3),
                onClick = {},
            )
            SettingsClickableItem(
                title = "Disabled item",
                summary = "Not available",
                icon = Icons.Outlined.PrivacyTip,
                enabled = false,
                shape = groupedItemShape(index = 2, count = 3),
                onClick = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SettingsSwitchItemPreview() {
    CameraPreviewColumn {
        Column(verticalArrangement = Arrangement.spacedBy(GROUPED_ITEM_SPACING)) {
            SettingsSwitchItem(
                title = "Camera sounds",
                summary = "Shutter sound and other audio cues",
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                checked = true,
                shape = groupedItemShape(index = 0, count = 3),
                onCheckedChange = {},
            )
            SettingsSwitchItem(
                title = "Gyroscope suggestions",
                summary = "Occasionally get visual suggestions",
                label = "Photo only",
                icon = Icons.Outlined.Straighten,
                checked = true,
                isChanged = true,
                shape = groupedItemShape(index = 1, count = 3),
                onCheckedChange = {},
            )
            SettingsSwitchItem(
                title = "Disabled option",
                summary = "Not available",
                icon = Icons.Outlined.PrivacyTip,
                checked = true,
                enabled = false,
                shape = groupedItemShape(index = 2, count = 3),
                onCheckedChange = {},
            )
        }
    }
}
