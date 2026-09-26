package app.grapheneos.camera.ui.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.annotation.StringRes
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.CapturedItem

private const val TAG = "CapturedItemIntents"

// Some OEM builds grant the shared uri to the target inside startActivity(), which throws a
// SecurityException when this app has itself lost access to the item (e.g. it was deleted
// externally, or its persisted uri came from a restored backup). Returns the error message to
// show, or null on success
@StringRes
internal fun shareCapturedItem(activity: Activity, item: CapturedItem): Int? {
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, item.uri)
        setDataAndType(item.uri, item.mimeType())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return try {
        val chooser = Intent.createChooser(intent, activity.getString(R.string.share_image))

        activity.startActivity(chooser)
        null
    } catch (e: SecurityException) {
        Log.e(TAG, "unable to share ${item.uiName()}", e)
        R.string.unable_to_share_media
    }
}

// The uri grant for a directly started editor is computed inside startActivity() on all Android
// versions (and inside the chooser start on the OEM builds mentioned above), failing the same way
// when the item is no longer accessible. Returns the error message to show, or null on success
@StringRes
internal fun editCapturedItem(
    activity: Activity,
    item: CapturedItem,
    useDefaultEditor: Boolean,
): Int? {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(item.uri, item.mimeType())
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return try {
        if (useDefaultEditor) {
            activity.startActivity(intent)
        } else {
            val chooser = Intent.createChooser(intent, activity.getString(R.string.edit_image))

            chooser.putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false)
            activity.startActivity(chooser)
        }
        null
    } catch (e: ActivityNotFoundException) {
        R.string.no_editor_app_error
    } catch (e: SecurityException) {
        Log.e(TAG, "unable to edit ${item.uiName()}", e)
        R.string.unable_to_edit_media
    }
}
