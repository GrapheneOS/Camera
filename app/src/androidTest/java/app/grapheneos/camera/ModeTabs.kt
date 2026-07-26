package app.grapheneos.camera

import androidx.test.core.app.ActivityScenario
import app.grapheneos.camera.ui.activities.MainActivity

/**
 * Waits until [scenario]'s mode tabs exist. The strip is built only once the vendor extension
 * probes report back, well after the camera binds, so anything that acts on a tab -- or on the
 * fling handlers that read the tab model -- has to wait for both.
 */
fun <A : MainActivity> awaitModeTabs(scenario: ActivityScenario<A>) {
    waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }
    waitUntil(scenario, "mode tabs are built") { it.tabLayout.tabCount > 0 }
}
