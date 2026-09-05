package app.grapheneos.camera.ui

import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MoreSettings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showStorageLocationNotFoundDialog(activity: MainActivity) {
    val dialog = MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.folder_not_found)
        .setMessage(R.string.reverting_to_default_folder)
        .setPositiveButton(R.string.ok, null)
        .setNeutralButton(R.string.more_settings) { _, _ ->
            MoreSettings.start(activity)
        }
        .create()

    dialog.setCancelable(false)
    dialog.showIgnoringShortEdgeMode()
}
