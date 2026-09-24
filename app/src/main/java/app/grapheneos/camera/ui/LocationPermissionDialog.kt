package app.grapheneos.camera.ui

import androidx.appcompat.app.AlertDialog
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showLocationPermissionDialog(
    activity: MainActivity,
    onSettingsClicked: (() -> Unit)?,
    onDismissed: () -> Unit,
): AlertDialog {
    return MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.location_permission_dialog_title)
        .setMessage(R.string.location_permission_dialog_message)
        .setSettingsButton(onSettingsClicked)
        .setOnDismissListener { onDismissed() }
        .showIgnoringShortEdgeMode()
}
