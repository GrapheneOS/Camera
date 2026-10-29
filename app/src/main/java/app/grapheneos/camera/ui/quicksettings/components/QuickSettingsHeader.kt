package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    onMoreClick: () -> Unit,
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
                text = when {
                    shownVideoMode -> stringResource(R.string.quick_settings_video_title)
                    else -> stringResource(R.string.quick_settings_photo_title)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        OutlinedIconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(
                width = MORE_BUTTON_WIDTH,
                height = MORE_BUTTON_HEIGHT,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = stringResource(R.string.camera_settings),
                modifier = Modifier.size(size = MORE_ICON_SIZE),
            )
        }
    }
}
