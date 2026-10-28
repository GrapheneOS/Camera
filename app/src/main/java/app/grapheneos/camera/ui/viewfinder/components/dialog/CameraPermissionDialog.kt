package app.grapheneos.camera.ui.viewfinder.components.dialog

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
internal fun CameraPermissionDialog(
    offersSettings: Boolean,
    onSettingsClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.camera_permission_dialog_title)) },
        text = { Text(text = stringResource(R.string.camera_permission_dialog_message)) },
        confirmButton = {
            when {
                offersSettings -> TextButton(onClick = onSettingsClick) {
                    Text(text = stringResource(R.string.settings))
                }

                else -> TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        },
        dismissButton = {
            if (offersSettings) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun CameraPermissionDialogPreview() {
    CameraPreviewTheme {
        CameraPermissionDialog(
            offersSettings = true,
            onSettingsClick = {},
            onDismiss = {},
        )
    }
}
