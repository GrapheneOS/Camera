package app.grapheneos.camera.ui.quicksettings.components

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.HighFrameRateLimit
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme

@Composable
internal fun HighFrameRateUnavailableDialog(
    limit: HighFrameRateLimit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.quick_settings_frame_rate_unavailable)) },
        text = { Text(text = stringResource(reasonOf(limit))) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        },
    )
}

@StringRes
private fun reasonOf(limit: HighFrameRateLimit): Int {
    return when (limit) {
        HighFrameRateLimit.HDR -> R.string.quick_settings_frame_rate_not_with_hdr
        HighFrameRateLimit.RESOLUTION -> R.string.quick_settings_frame_rate_not_at_resolution
    }
}

@PreviewLightDark
@Composable
private fun HighFrameRateUnavailableDialogPreview() {
    CameraPreviewTheme {
        HighFrameRateUnavailableDialog(
            limit = HighFrameRateLimit.HDR,
            onDismiss = {},
        )
    }
}
