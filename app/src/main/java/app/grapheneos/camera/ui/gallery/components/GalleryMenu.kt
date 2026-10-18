package app.grapheneos.camera.ui.gallery.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.CameraPreviewTheme

private val MENU_WIDTH = 224.dp
private val ITEM_HORIZONTAL_PADDING = 16.dp
private val ICON_END_PADDING = 4.dp

@Composable
internal fun GalleryMenu(
    showsExternalActions: Boolean,
    onInfoClick: () -> Unit,
    onEditWithClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

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
        ) {
            MenuItem(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.file_info),
                onClick = {
                    isExpanded = false
                    onInfoClick()
                },
            )
            if (showsExternalActions) {
                MenuItem(
                    icon = Icons.Outlined.Edit,
                    label = stringResource(R.string.edit_with),
                    onClick = {
                        isExpanded = false
                        onEditWithClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = ICON_END_PADDING),
            )
        },
        contentPadding = PaddingValues(horizontal = ITEM_HORIZONTAL_PADDING),
    )
}

@PreviewLightDark
@Composable
private fun GalleryMenuPreview() {
    CameraPreviewTheme {
        GalleryMenu(
            showsExternalActions = true,
            onInfoClick = {},
            onEditWithClick = {},
        )
    }
}
