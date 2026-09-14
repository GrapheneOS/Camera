package app.grapheneos.camera

import android.app.Application
import app.grapheneos.camera.ui.activities.InAppGallery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CountDownLatch

/**
 * Holds the gallery's media scan until [gate] opens, so gallerySliderAdapter stays null, like on a
 * slow device.
 */
fun stalledMediaScan(gate: CountDownLatch): Application.ActivityLifecycleCallbacks {
    return mockk(relaxed = true) {
        every { onActivityPreCreated(ofType<InAppGallery>(), any()) } answers {
            firstArg<InAppGallery>().asyncLoaderOfCapturedItems.execute {
                // Interrupted by shutdownNow() when the activity is destroyed
                try {
                    gate.await()
                } catch (_: InterruptedException) {
                }
            }
        }
    }
}
