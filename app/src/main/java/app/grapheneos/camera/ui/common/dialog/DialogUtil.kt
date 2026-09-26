package app.grapheneos.camera.ui.common.dialog

import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import app.grapheneos.camera.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * When in an activity where the status bar is hidden, the window layoutInDisplayCutoutMode
 * is set to [WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES], and a
 * material alert dialog is present that is large enough, the layout of the dialog will appear
 * broken and sometimes will shift randomly. These extensions force the dialog window to ignore
 * the short edges mode so that it will appear as normal.
 */

fun AlertDialog.ignoreShortEdges() {
    window?.attributes?.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
}

fun AlertDialog.showIgnoringShortEdgeMode(): AlertDialog {
    ignoreShortEdges()
    show()
    return this
}

fun MaterialAlertDialogBuilder.showIgnoringShortEdgeMode(): AlertDialog =
    this.create().showIgnoringShortEdgeMode()

fun MaterialAlertDialogBuilder.setSettingsButton(
    onClick: (() -> Unit)?,
): MaterialAlertDialogBuilder {
    return apply {
        onClick?.let { setPositiveButton(R.string.settings) { _, _ -> it() } }
    }
}
