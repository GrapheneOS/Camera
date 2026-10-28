package app.grapheneos.camera.ui.common.dialog

import android.os.Build
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showPictureFailureDialog(
    activity: MainActivity,
    message: String,
    stackTrace: String,
    onCopyDetails: (String) -> Unit,
) {
    MaterialAlertDialogBuilder(activity).apply {
        setMessage(message)
        setPositiveButton(R.string.show_details) { _, _ ->
            showPictureFailureDetailsDialog(activity, stackTrace, onCopyDetails)
        }
        showIgnoringShortEdgeMode()
    }
}

private fun showPictureFailureDetailsDialog(
    activity: MainActivity,
    stackTrace: String,
    onCopyDetails: (String) -> Unit,
) {
    val packageName = activity.packageName
    val packageVersion = activity.packageManager
        .getPackageInfo(packageName, 0)
        .longVersionCode
    val text = "osVersion: ${Build.FINGERPRINT}" +
        "\npackage: $packageName:$packageVersion" +
        "\n\n$stackTrace"

    MaterialAlertDialogBuilder(activity).apply {
        setItems(text.lines().toTypedArray(), null)
        setNeutralButton(R.string.copy_to_clipboard) { _, _ ->
            onCopyDetails(text)
        }
        showIgnoringShortEdgeMode()
    }
}
