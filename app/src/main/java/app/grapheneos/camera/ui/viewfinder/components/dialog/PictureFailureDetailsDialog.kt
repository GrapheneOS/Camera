package app.grapheneos.camera.ui.viewfinder.components.dialog

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme

@Composable
internal fun PictureFailureDetailsDialog(
    details: String,
    onCopyClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        text = {
            Text(
                text = details,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCopyClick) {
                Text(text = stringResource(R.string.copy_to_clipboard))
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun PictureFailureDetailsDialogPreview() {
    CameraPreviewTheme {
        PictureFailureDetailsDialog(
            details = "osVersion: google/akita/akita:16/BP2A/2025:user/release-keys" +
                "\npackage: app.grapheneos.camera:42" +
                "\n\njava.io.IOException: no camera",
            onCopyClick = {},
            onDismiss = {},
        )
    }
}
