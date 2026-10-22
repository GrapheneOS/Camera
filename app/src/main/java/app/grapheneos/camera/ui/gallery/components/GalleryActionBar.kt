package app.grapheneos.camera.ui.gallery.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme

private val BAR_HEIGHT = 60.dp
private val ITEM_TOP_PADDING = 12.dp
private val ICON_LABEL_SPACING = 4.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryActionBar(
    showsExternalActions: Boolean,
    onShareClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .heightIn(min = BAR_HEIGHT),
        ) {
            if (showsExternalActions) {
                ActionItem(
                    icon = Icons.Outlined.Share,
                    label = stringResource(R.string.gallery_share),
                    onClick = onShareClick,
                    modifier = Modifier.weight(1f),
                )
                ActionItem(
                    icon = Icons.Outlined.Tune,
                    label = stringResource(R.string.edit),
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f),
                )
            }
            ActionItem(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.delete),
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = null,
                    indication = ripple(bounded = false),
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(top = ITEM_TOP_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.height(ICON_LABEL_SPACING))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun GalleryActionBarPreview() {
    CameraPreviewTheme {
        GalleryActionBar(
            showsExternalActions = true,
            onShareClick = {},
            onEditClick = {},
            onDeleteClick = {},
        )
    }
}
