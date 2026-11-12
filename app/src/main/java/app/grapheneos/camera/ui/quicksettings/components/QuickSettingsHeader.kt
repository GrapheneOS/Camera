package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.animation.crossfade

private val HORIZONTAL_PADDING = 16.dp
private val BOTTOM_PADDING = 8.dp
private val TITLE_SPACING = 16.dp
private val MORE_BUTTON_WIDTH = 52.dp
private val MORE_BUTTON_HEIGHT = 40.dp
private val MORE_ICON_SIZE = 20.dp

@Composable
internal fun QuickSettingsHeader(
    isVideoMode: Boolean,
    moreButtonVisible: Boolean,
    enabled: Boolean,
    onMoreClick: () -> Unit,
    onMoreLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = HORIZONTAL_PADDING,
                end = HORIZONTAL_PADDING,
                bottom = BOTTOM_PADDING,
            ),
        horizontalArrangement = Arrangement.spacedBy(space = TITLE_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = isVideoMode,
            modifier = Modifier.weight(1f),
            transitionSpec = { crossfade() },
        ) { shownVideoMode ->
            Text(
                text = quickSettingsTitle(isVideoMode = shownVideoMode),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(
                    width = MORE_BUTTON_WIDTH,
                    height = MORE_BUTTON_HEIGHT,
                ),
        ) {
            if (moreButtonVisible) {
                MoreButton(
                    enabled = enabled,
                    onClick = onMoreClick,
                    onLongClick = onMoreLongClick,
                )
            }
        }
    }
}

@Composable
internal fun quickSettingsTitle(isVideoMode: Boolean): String {
    return when {
        isVideoMode -> stringResource(R.string.quick_settings_video_title)
        else -> stringResource(R.string.quick_settings_photo_title)
    }
}

@Composable
private fun MoreButton(
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = IconButtonDefaults.outlinedShape

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape = shape)
            .border(
                border = IconButtonDefaults.outlinedIconButtonBorder(enabled = true),
                shape = shape,
            )
            .combinedClickable(
                role = Role.Button,
                enabled = enabled,
                onLongClickLabel = stringResource(R.string.quick_settings_copy_version),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.MoreHoriz,
            contentDescription = stringResource(R.string.camera_settings),
            modifier = Modifier.size(size = MORE_ICON_SIZE),
        )
    }
}
