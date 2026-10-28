package app.grapheneos.camera.ui.viewfinder.components.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.window.DialogProperties
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme

@Composable
internal fun StorageLocationNotFoundDialog(
    onDismiss: () -> Unit,
    onCameraSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = modifier,
        title = { Text(text = stringResource(R.string.folder_not_found)) },
        text = { Text(text = stringResource(R.string.reverting_to_default_folder)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCameraSettingsClick) {
                Text(text = stringResource(R.string.camera_settings))
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}

@PreviewLightDark
@Composable
private fun StorageLocationNotFoundDialogPreview() {
    CameraPreviewTheme {
        StorageLocationNotFoundDialog(
            onDismiss = {},
            onCameraSettingsClick = {},
        )
    }
}
