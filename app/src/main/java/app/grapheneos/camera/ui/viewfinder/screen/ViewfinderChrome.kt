package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.ui.activities.MainActivity

interface ViewfinderChrome {

    fun forceUpdateOrientationSensor()
}

internal class ViewfinderChromeImpl(
    private val activity: MainActivity,
) : ViewfinderChrome {

    override fun forceUpdateOrientationSensor() {
        activity.forceUpdateOrientationSensor()
    }
}
