package app.grapheneos.camera.ui

import app.grapheneos.camera.R
import app.grapheneos.camera.domain.qr.BarcodeFormats
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showMoreQrFormatOptions(
    activity: MainActivity,
    barcodeFormats: BarcodeFormats,
    onApplied: () -> Unit,
) {
    val optionNames = barcodeFormats.uncommonNames()

    showQrFormatsDialog(
        activity = activity,
        optionNames = optionNames,
        initialValues = optionNames.map { barcodeFormats.isEnabled(it) },
        onConfirm = { values ->
            val selection = optionNames
                .withIndex()
                .associate { (index, name) -> name to values[index] }

            when {
                barcodeFormats.apply(selection) -> onApplied()
                else -> activity.showMessage(R.string.no_barcode_selected)
            }
        },
    )
}

private fun showQrFormatsDialog(
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
