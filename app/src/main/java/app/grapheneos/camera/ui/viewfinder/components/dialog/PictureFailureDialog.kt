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
internal fun PictureFailureDialog(
    message: String,
    onShowDetailsClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onShowDetailsClick) {
                Text(text = stringResource(R.string.show_details))
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun PictureFailureDialogPreview() {
    CameraPreviewTheme {
        PictureFailureDialog(
            message = "Unable to capture image, error code: 2",
            onShowDetailsClick = {},
            onDismiss = {},
        )
    }
}
