package app.grapheneos.camera.ui.quicksettings.components

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
internal fun OptionUnavailableDialog(
    title: String,
    reason: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = title) },
        text = { Text(text = reason) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun OptionUnavailableDialogPreview() {
    CameraPreviewTheme {
        OptionUnavailableDialog(
            title = stringResource(R.string.quick_settings_frame_rate_unavailable),
            reason = stringResource(R.string.quick_settings_frame_rate_not_with_hdr),
            onDismiss = {},
        )
    }
}
