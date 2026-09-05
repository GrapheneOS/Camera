package app.grapheneos.camera.ui

import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showQrFormatsDialog(
    activity: MainActivity,
    optionNames: List<String>,
    initialValues: List<Boolean>,
    onConfirm: (List<Boolean>) -> Unit,
) {
    val values = initialValues.toBooleanArray()

    MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.more_options)
        .setMultiChoiceItems(optionNames.toTypedArray(), values) { _, index, isChecked ->
            values[index] = isChecked
        }
        .setPositiveButton(R.string.ok) { _, _ ->
            onConfirm(values.toList())
        }
        .setNegativeButton(R.string.cancel, null)
        .create()
        .showIgnoringShortEdgeMode()
}
