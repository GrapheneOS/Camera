package app.grapheneos.camera.ui.common.dialog

import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showStorageLocationNotFoundDialog(
    activity: MainActivity,
    onCameraSettingsClicked: () -> Unit,
) {
    val dialog = MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.folder_not_found)
        .setMessage(R.string.reverting_to_default_folder)
        .setPositiveButton(R.string.ok, null)
        .setNeutralButton(R.string.camera_settings) { _, _ ->
            onCameraSettingsClicked()
        }
        .create()

    dialog.setCancelable(false)
    dialog.showIgnoringShortEdgeMode()
}
