package app.grapheneos.camera

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.CountDownTimer
import android.view.WindowManager
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.color.DynamicColors

class App : Application() {

    companion object {
        private const val STALE_LOCATION_THRESHOLD = 15 * 1000L
    }

    private var activity: MainActivity? = null
    private var location: Location? = null

    private var isLocationFetchInProgress = false

    private val locationManager by lazy {
        getSystemService(LocationManager::class.java)!!
    }

    private val locationListener: LocationListener by lazy {
        object : LocationListener {
            override fun onLocationChanged(changedLocation: Location) {
                location = listOf(location, changedLocation).getOptimalLocation()
            }

            override fun onProviderDisabled(provider: String) {
                if (!isAnyLocationProvideActive()) {
                    activity?.indicateLocationProvidedIsDisabled()
                }
            }

            override fun onLocationChanged(locations: MutableList<Location>) {
                val location = locations.getOptimalLocation()
                if (location != null) {
                    this@App.location = location
                }
            }

            override fun onProviderEnabled(provider: String) {}
        }
    }

    private val autoSleepDuration: Long = 5 * 60 * 1000 // 5 minutes
    private val autoSleepTimer = object : CountDownTimer(
        autoSleepDuration,
        autoSleepDuration / 2
    ) {
        override fun onTick(milliLeft: Long) {}

        override fun onFinish() {
            activity?.enableAutoSleep()
        }
    }

    private val activityLifeCycleHelper by lazy {
        ActivityLifeCycleHelper { activity ->
            if (activity != null) activity.disableAutoSleep() else this.activity?.enableAutoSleep()
            this.activity = activity
        }
    }

    fun isAnyLocationProvideActive(): Boolean {
        if (!locationManager.isLocationEnabled) return false
        val providers = locationManager.allProviders

        providers.forEach {
            if (locationManager.isProviderEnabled(it)) return true
        }
        return false
    }

    fun List<Location?>.getOptimalLocation(): Location? {
        if (isNullOrEmpty()) return null

        var optimalLocation: Location? = null
        forEach { location ->
            if (location != null) {
                if (optimalLocation == null) {
                    optimalLocation = location
                    return@forEach
                }

                val timeDifference = (location.time - optimalLocation.time)

                // If the location is older than STALE_LOCATION_THRESHOLD ms
                if (timeDifference > STALE_LOCATION_THRESHOLD) {
                    optimalLocation = location
                } else {
                    // Compare their accuracy instead of time if the difference is below
                    // threshold
                    if (location.accuracy > optimalLocation.accuracy) {
                        optimalLocation = location
                    }
                }
            }
        }
        return optimalLocation
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activityLifeCycleHelper)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_COARSE_LOCATION])
    fun requestLocationUpdates(reAttach: Boolean = false) {
        if (!isLocationEnabled()) {
            activity?.indicateLocationProvidedIsDisabled()
        }
        if (isLocationFetchInProgress) {
            if (!reAttach) return
            dropLocationUpdates()
        }
        isLocationFetchInProgress = true
        if (location == null) {
            val providers = locationManager.allProviders
            val locations = providers.map {
                locationManager.getLastKnownLocation(it)
            }
            val fetchedLocation = locations.getOptimalLocation()
            if (fetchedLocation != null) {
                location = fetchedLocation
            }
        }

        locationManager.allProviders.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                2000,
                10f,
                locationListener
            )
        }
    }

    fun dropLocationUpdates() {
        isLocationFetchInProgress = false
        locationManager.removeUpdates(locationListener)
    }

    fun getLocation(): Location? = location

    private fun isLocationEnabled(): Boolean = locationManager.isLocationEnabled

    fun shouldAskForLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED

    override fun onTerminate() {
        super.onTerminate()
        unregisterActivityLifecycleCallbacks(activityLifeCycleHelper)
    }

    private fun AppCompatActivity.disableAutoSleep() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        resetPreventScreenFromSleeping()
    }

    fun resetPreventScreenFromSleeping() {
        autoSleepTimer.cancel()
        autoSleepTimer.start()
    }

    private fun AppCompatActivity.enableAutoSleep() {
        autoSleepTimer.cancel()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
