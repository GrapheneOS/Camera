package app.grapheneos.camera

import android.app.Application
import app.grapheneos.camera.ui.gallery.GalleryActivity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation

/**
 * Holds the gallery's media scan until the activity is destroyed, so gallerySliderAdapter stays
 * null, like on a slow device.
 */
fun stalledMediaScan(): Application.ActivityLifecycleCallbacks {
    return mockk(relaxed = true) {
        every { onActivityPreCreated(ofType<GalleryActivity>(), any()) } answers {
            firstArg<GalleryActivity>().scanCapturedItems = { awaitCancellation() }
        }
    }
}
