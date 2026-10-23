package app.grapheneos.camera.ui.mediaviewer.navigation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.util.queryIntentActivities
import app.grapheneos.camera.util.resolveActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal interface SystemGalleryLauncher {

    fun canOpen(): Boolean

    fun open(activity: Activity)

    fun openItem(
        activity: Activity,
        item: CapturedItem,
    )
}

internal class SystemGalleryLauncherImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemGalleryLauncher {

    override fun canOpen(): Boolean {
        return galleryIntent().resolveActivity(context.packageManager) != null
    }

    override fun open(activity: Activity) {
        activity.startActivity(galleryIntent())
    }

    override fun openItem(
        activity: Activity,
        item: CapturedItem,
    ) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, item.mimeType())
            setPackage(galleryPackage())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "the gallery can't view ${item.uiName()}", e)
            open(activity)
        } catch (e: SecurityException) {
            Log.e(TAG, "unable to grant ${item.uiName()} to the gallery", e)
            open(activity)
        }
    }

    // Without a default gallery this resolves to the system's chooser, not to a gallery.
    private fun galleryPackage(): String? {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_GALLERY)
        val galleries = packageManager
            .queryIntentActivities(intent, 0L)
            .map { it.activityInfo.packageName }
        val preferred = packageManager.resolveActivity(intent, 0L)?.activityInfo?.packageName

        return preferred?.takeIf { it in galleries }
    }

    private fun galleryIntent(): Intent {
        return Intent
            .makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private companion object {
        private const val TAG = "SystemGalleryLauncher"
    }
}
