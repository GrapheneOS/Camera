package app.grapheneos.camera

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CountDownTimer
import android.os.SystemClock
import android.view.WindowManager
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.camera.capturer.deleteStalePendingRecordings
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.color.DynamicColors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class App : Application() {

    companion object {
        // Must stay above the ~10 min throttle the OS applies to coarse-only apps.
        private const val MAX_LOCATION_AGE = 15 * 60 * 1000L
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
                location = getOptimalLocation(listOf(location, changedLocation))
            }

            override fun onProviderDisabled(provider: String) {
                if (!isAnyLocationProvideActive()) {
                    activity?.indicateLocationProvidedIsDisabled()
                }
            }

            override fun onLocationChanged(locations: MutableList<Location>) {
                location = getOptimalLocation(locations + location)
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

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activityLifeCycleHelper)
        DynamicColors.applyToActivitiesIfAvailable(this)

        thread {
            deleteStalePendingRecordings(this)
        }
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

        val providers = locationProviders

        val lastKnownLocations = providers.map {
            locationManager.getLastKnownLocation(it)
        }
        location = getOptimalLocation(lastKnownLocations + location)

        providers.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                2000,
                0f,
                locationListener
            )
        }
    }

    private val locationProviders: List<String>
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                locationManager.hasProvider(LocationManager.FUSED_PROVIDER)
            ) {
                return listOf(LocationManager.FUSED_PROVIDER)
            }
            return locationManager.allProviders.filter {
                it == LocationManager.GPS_PROVIDER ||
                    it == LocationManager.NETWORK_PROVIDER ||
                    it == LocationManager.PASSIVE_PROVIDER
            }
        }

    fun dropLocationUpdates() {
        isLocationFetchInProgress = false
        locationManager.removeUpdates(locationListener)
    }

    fun disableLocationFetching() {
        dropLocationUpdates()
        location = null
    }

    fun getLocation(): Location? {
        val location = this.location ?: return null
        val ageMs = TimeUnit.NANOSECONDS.toMillis(
            SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        )
        if (ageMs > MAX_LOCATION_AGE) {
            this.location = null
            return null
        }
        return location
    }

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
