package app.grapheneos.camera.ui.viewfinder.screen

interface ViewfinderChrome {

    fun updateLastFrame()

    fun cancelPendingCapture()

    fun forceUpdateOrientationSensor()

    fun startFocusTimer()

    fun cancelFocusTimer()
}
