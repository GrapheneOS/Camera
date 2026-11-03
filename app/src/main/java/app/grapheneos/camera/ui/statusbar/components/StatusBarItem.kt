package app.grapheneos.camera.ui.statusbar.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.components.motion.LocalContentRotation
import app.grapheneos.camera.ui.core.model.QuickSetting
import app.grapheneos.camera.ui.settingvalues.flashIcon
import app.grapheneos.camera.ui.settingvalues.flashLabel
import app.grapheneos.camera.ui.settingvalues.selfTimerLabel
import app.grapheneos.camera.ui.settingvalues.videoQualityLabel
import app.grapheneos.camera.ui.statusbar.screen.model.StatusItemUiModel

private val BADGE_HORIZONTAL_PADDING = 4.dp
private val BADGE_VERTICAL_PADDING = 1.dp
private val BADGE_TEXT_SIZE = 14.dp
private val BADGE_LINE_HEIGHT = 20.dp

@Composable
internal fun StatusItem(
    item: StatusItemUiModel,
    enabled: Boolean,
    onClick: (QuickSetting) -> Unit,
) {
    val on = stringResource(R.string.quick_settings_on)

    when (item) {
        is StatusItemUiModel.SelfTimer -> {
            val value = selfTimerLabel(item.duration)

            StatusBarButton(
                description = statusDescription(R.string.timer, value),
                enabled = enabled,
                onClick = { onClick(QuickSetting.SELF_TIMER) },
            ) {
                StatusBadge(text = value)
            }
        }

        is StatusItemUiModel.Flash -> StatusBarButton(
            description = statusDescription(R.string.flash_mode, flashLabel(item.mode)),
            enabled = enabled,
            onClick = { onClick(QuickSetting.FLASH) },
        ) {
            StatusIcon(icon = flashIcon(item.mode))
        }

        StatusItemUiModel.SelfIllumination -> StatusBarButton(
            description = statusDescription(R.string.self_illumination, on),
            enabled = enabled,
            onClick = { onClick(QuickSetting.SELF_ILLUMINATION) },
        ) {
            StatusIcon(icon = Icons.Outlined.Lightbulb)
        }

        is StatusItemUiModel.Resolution -> {
            val value = videoQualityLabel(item.quality)

            StatusBarButton(
                description = statusDescription(R.string.quick_settings_resolution, value),
                enabled = enabled,
                onClick = { onClick(QuickSetting.VIDEO_QUALITY) },
            ) {
                StatusBadge(text = value)
            }
        }
    }
}

@Composable
private fun statusDescription(
    @StringRes labelId: Int,
    value: String,
): String {
    return stringResource(
        R.string.quick_settings_option_description,
        stringResource(labelId),
        value,
    )
}

@Composable
internal fun StatusBarButton(
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val contentColor = LocalContentColor.current

    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(disabledContentColor = contentColor),
        content = content,
    )
}

@Composable
internal fun StatusIcon(icon: ImageVector) {
    val rotation = LocalContentRotation.current

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.graphicsLayer { rotationZ = rotation() },
    )
}

@Composable
private fun StatusBadge(text: String) {
    val rotation = LocalContentRotation.current
    val density = LocalDensity.current

    Text(
        text = text,
        modifier = Modifier
            .graphicsLayer { rotationZ = rotation() }
            .background(
                color = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.extraSmall,
            )
            .padding(
                horizontal = BADGE_HORIZONTAL_PADDING,
                vertical = BADGE_VERTICAL_PADDING,
            ),
        color = MaterialTheme.colorScheme.surface,
        fontSize = with(density) { BADGE_TEXT_SIZE.toSp() },
        fontWeight = FontWeight.Bold,
        lineHeight = with(density) { BADGE_LINE_HEIGHT.toSp() },
        maxLines = 1,
    )
}
