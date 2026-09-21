package app.grapheneos.camera.ui

import android.os.Build
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.viewfinder.screen.model.PictureFailureDetails
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showPictureFailureDialog(
    activity: MainActivity,
    message: String,
    details: PictureFailureDetails,
    onCopyDetails: (String) -> Unit,
) {
    MaterialAlertDialogBuilder(activity).apply {
        setMessage(message)
        setPositiveButton(R.string.show_details) { _, _ ->
            showPictureFailureDetailsDialog(activity, details, onCopyDetails)
        }
        showIgnoringShortEdgeMode()
    }
}

private fun showPictureFailureDetailsDialog(
    activity: MainActivity,
    details: PictureFailureDetails,
    onCopyDetails: (String) -> Unit,
) {
    val packageName = activity.packageName
    val packageVersion = activity.packageManager
        .getPackageInfo(packageName, 0)
        .longVersionCode
    val text = "osVersion: ${Build.FINGERPRINT}" +
        "\npackage: $packageName:$packageVersion" +
        "\n\n${details.stackTrace}"

    MaterialAlertDialogBuilder(activity).apply {
        setItems(text.lines().toTypedArray(), null)
        setNeutralButton(R.string.copy_to_clipboard) { _, _ ->
            onCopyDetails(text)
        }
        showIgnoringShortEdgeMode()
    }
}
