package app.grapheneos.camera.ui.gallery.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme

private val SHEET_HORIZONTAL_PADDING = 16.dp
private val MESSAGE_TOP_PADDING = 24.dp
private val MESSAGE_BOTTOM_PADDING = 12.dp
private val ACTION_HEIGHT = 56.dp
private val ACTION_ICON_SPACING = 32.dp
private val SHEET_BOTTOM_PADDING = 8.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteConfirmationSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        DeleteConfirmationContent(
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun DeleteConfirmationContent(
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = SHEET_BOTTOM_PADDING)) {
        Text(
            text = stringResource(R.string.delete_description),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(
                start = SHEET_HORIZONTAL_PADDING,
                top = MESSAGE_TOP_PADDING,
                end = SHEET_HORIZONTAL_PADDING,
                bottom = MESSAGE_BOTTOM_PADDING,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ACTION_HEIGHT)
                .clickable(
                    role = Role.Button,
                    onClick = onConfirm,
                )
                .padding(horizontal = SHEET_HORIZONTAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(ACTION_ICON_SPACING))
            Text(
                text = stringResource(R.string.delete),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DeleteConfirmationContentPreview() {
    CameraPreviewTheme {
        DeleteConfirmationContent(
            onConfirm = {},
        )
    }
}
