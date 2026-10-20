package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme

@Composable
internal fun SettingsConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun SettingsConfirmDialogPreview() {
    CameraPreviewTheme {
        SettingsConfirmDialog(
            title = "Reset camera settings?",
            message = "Every setting on this screen goes back to its default.",
            confirmLabel = "Reset",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
