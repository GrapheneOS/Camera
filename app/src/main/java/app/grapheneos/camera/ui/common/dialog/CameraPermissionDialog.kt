package app.grapheneos.camera.ui.common.dialog

import androidx.appcompat.app.AlertDialog
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showCameraPermissionDialog(
    activity: MainActivity,
    onSettingsClicked: (() -> Unit)?,
    onDismissed: () -> Unit,
): AlertDialog {
    return MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.camera_permission_dialog_title)
        .setMessage(R.string.camera_permission_dialog_message)
        .setSettingsButton(onSettingsClicked)
        .setNegativeButton(R.string.cancel, null)
        .setOnDismissListener { onDismissed() }
        .showIgnoringShortEdgeMode()
}
