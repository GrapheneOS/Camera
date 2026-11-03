package app.grapheneos.camera.ui.gallery.navigation

import android.app.Activity
import android.content.Intent
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.di.core.cameraEntryPointArguments
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.gallery.GalleryActivity
import app.grapheneos.camera.ui.gallery.SecureGalleryActivity
import app.grapheneos.camera.ui.gallery.screen.galleryArguments
import javax.inject.Inject

internal interface GalleryIntents {

    fun open(
        activity: Activity,
        entryPoint: CameraEntryPoint,
        lastCapturedItem: CapturedItem?,
    )

    fun openSecure(
        activity: Activity,
        entryPoint: CameraEntryPoint,
        capturedItems: List<CapturedItem>,
        lastCapturedItem: CapturedItem?,
    )
}

internal class GalleryIntentsImpl @Inject constructor() : GalleryIntents {

    override fun open(
        activity: Activity,
        entryPoint: CameraEntryPoint,
        lastCapturedItem: CapturedItem?,
    ) {
        startGallery(
            activity = activity,
            screen = GalleryActivity::class.java,
            entryPoint = entryPoint,
            sessionItems = null,
            lastCapturedItem = lastCapturedItem,
        )
    }

    override fun openSecure(
        activity: Activity,
        entryPoint: CameraEntryPoint,
        capturedItems: List<CapturedItem>,
        lastCapturedItem: CapturedItem?,
    ) {
        startGallery(
            activity = activity,
            screen = SecureGalleryActivity::class.java,
            entryPoint = entryPoint,
            sessionItems = capturedItems,
            lastCapturedItem = lastCapturedItem,
        )
    }

    private fun startGallery(
        activity: Activity,
        screen: Class<out Activity>,
        entryPoint: CameraEntryPoint,
        sessionItems: List<CapturedItem>?,
        lastCapturedItem: CapturedItem?,
    ) {
        val intent = Intent(activity, screen)
            .putExtras(cameraEntryPointArguments(entryPoint))
            .putExtras(
                galleryArguments(
                    sessionItems = sessionItems,
                    lastCapturedItem = lastCapturedItem,
                ),
            )

        activity.startActivity(intent)
    }
}
