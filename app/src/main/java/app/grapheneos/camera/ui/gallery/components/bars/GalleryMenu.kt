package app.grapheneos.camera.ui.gallery.components.bars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.common.components.crossedOut
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.gallery.screen.model.GalleryMenuUiModel
import app.grapheneos.camera.ui.mediaviewer.components.video.playbackSpeedLabel

private val MENU_WIDTH = 224.dp
private val ITEM_HORIZONTAL_PADDING = 16.dp
private val ICON_END_PADDING = 4.dp

@Composable
internal fun GalleryMenu(
    menu: GalleryMenuUiModel,
    showsExternalActions: Boolean,
    onInfoClick: () -> Unit,
    onEditWithClick: () -> Unit,
    onUseAsClick: () -> Unit,
    onLoopClick: () -> Unit,
    onPlaybackSpeedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    fun closing(action: () -> Unit): () -> Unit {
        return {
            isExpanded = false
            action()
        }
    }

    Box(modifier = modifier) {
        IconButton(onClick = { isExpanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.more_options),
            )
        }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            modifier = Modifier.width(MENU_WIDTH),
            shape = MaterialTheme.shapes.medium,
        ) {
            MenuItem(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.file_info),
                onClick = closing(onInfoClick),
            )
            if (showsExternalActions) {
                MenuItem(
                    icon = Icons.Outlined.Edit,
                    label = stringResource(R.string.edit_with),
                    onClick = closing(onEditWithClick),
                )
            }
            when (menu) {
                is GalleryMenuUiModel.Photo -> if (showsExternalActions) {
                    MenuItem(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        label = stringResource(R.string.use_as),
                        onClick = closing(onUseAsClick),
                    )
                }

                is GalleryMenuUiModel.Video -> VideoMenuItems(
                    menu = menu,
                    onLoopClick = closing(onLoopClick),
                    onPlaybackSpeedClick = closing(onPlaybackSpeedClick),
                )
            }
        }
    }
}

@Composable
private fun VideoMenuItems(
    menu: GalleryMenuUiModel.Video,
    onLoopClick: () -> Unit,
    onPlaybackSpeedClick: () -> Unit,
) {
    MenuItem(
        icon = Icons.Outlined.Repeat,
        isIconCrossedOut = !menu.isLooping,
        label = stringResource(R.string.loop_video),
        onClick = onLoopClick,
        trailing = {
            MenuText(
                stringResource(
                    when {
                        menu.isLooping -> R.string.loop_video_on
                        else -> R.string.loop_video_off
                    },
                ),
            )
        },
    )
    MenuItem(
        icon = Icons.Outlined.Speed,
        label = stringResource(R.string.playback_speed),
        onClick = onPlaybackSpeedClick,
        trailing = { MenuText(playbackSpeedLabel(menu.playbackSpeed)) },
    )
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isIconCrossedOut: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    DropdownMenuItem(
        text = { MenuText(label) },
        onClick = onClick,
        leadingIcon = {
            MenuIcon(
                icon = icon,
                isCrossedOut = isIconCrossedOut,
            )
        },
        trailingIcon = trailing,
        contentPadding = PaddingValues(horizontal = ITEM_HORIZONTAL_PADDING),
    )
}

@Composable
private fun MenuIcon(
    icon: ImageVector,
    isCrossedOut: Boolean,
) {
    val color = LocalContentColor.current

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .padding(end = ICON_END_PADDING)
            .crossedOut(
                isCrossedOut = isCrossedOut,
                color = color,
            ),
    )
}

@Composable
private fun MenuText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@PreviewLightDark
@Composable
private fun GalleryMenuPreview() {
    CameraPreviewTheme {
        GalleryMenu(
            menu = GalleryMenuUiModel.Video(
                isLooping = false,
                playbackSpeed = 1.5f,
            ),
            showsExternalActions = true,
            onInfoClick = {},
            onEditWithClick = {},
            onUseAsClick = {},
            onLoopClick = {},
            onPlaybackSpeedClick = {},
        )
    }
}
