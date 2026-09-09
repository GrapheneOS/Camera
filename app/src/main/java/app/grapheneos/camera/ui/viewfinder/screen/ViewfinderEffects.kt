package app.grapheneos.camera.ui.viewfinder.screen

interface ViewfinderEffects {

    fun updateLastFrame()

    fun cancelPendingCapture()

    fun forceUpdateOrientationSensor()

    fun hideExposurePanel()

    fun startFocusTimer()

    fun cancelFocusTimer()
}
