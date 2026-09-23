package app.grapheneos.camera.ui

import androidx.appcompat.app.AlertDialog
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showMicrophonePermissionDialog(
    activity: MainActivity,
    onSettingsClicked: () -> Unit,
    onRecordWithoutAudioClicked: () -> Unit,
    onDismissed: () -> Unit,
): AlertDialog {
    return MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.audio_permission_dialog_title)
        .setMessage(R.string.audio_permission_dialog_message)
        .setPositiveButton(R.string.settings) { _, _ -> onSettingsClicked() }
        .setNegativeButton(R.string.cancel, null)
        .setNeutralButton(R.string.disable_audio) { _, _ -> onRecordWithoutAudioClicked() }
        .setOnDismissListener { onDismissed() }
        .showIgnoringShortEdgeMode()
}
