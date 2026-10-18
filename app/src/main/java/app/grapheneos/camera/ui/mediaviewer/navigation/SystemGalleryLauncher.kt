package app.grapheneos.camera.ui.mediaviewer.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal interface SystemGalleryLauncher {

    fun canOpen(): Boolean

    fun open(activity: Activity)
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

    private fun galleryIntent(): Intent {
        return Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
    }
}
