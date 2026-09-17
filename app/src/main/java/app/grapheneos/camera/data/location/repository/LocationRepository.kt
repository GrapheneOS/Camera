package app.grapheneos.camera.data.location.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import app.grapheneos.camera.getOptimalLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface LocationRepository {

    val providersDisabled: Flow<Unit>

    fun shouldAskForPermission(): Boolean

    fun isLocationEnabled(): Boolean

    fun isAnyProviderEnabled(): Boolean

    fun currentLocation(): Location?

    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    fun startUpdates(reattach: Boolean)

    fun pauseUpdates()

    fun stopUpdates()
}

internal class LocationRepositoryImpl @Inject constructor(
    private val locationManager: LocationManager,
    @ApplicationContext private val context: Context,
) : LocationRepository {

    private val _providersDisabled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val providersDisabled: Flow<Unit> = _providersDisabled.asSharedFlow()

    private var location: Location? = null
    private var isUpdating = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(changedLocation: Location) {
            location = getOptimalLocation(listOf(location, changedLocation))
        }

        override fun onLocationChanged(locations: MutableList<Location>) {
            location = getOptimalLocation(locations + location)
        }

        override fun onProviderDisabled(provider: String) {
            if (!isAnyProviderEnabled()) {
                _providersDisabled.tryEmit(Unit)
            }
        }

        override fun onProviderEnabled(provider: String) {
            // Still abstract on API 29, so the override stays even with nothing to do.
        }
    }

    private val providers: List<String>
        get() {
            val hasFusedProvider = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                locationManager.hasProvider(LocationManager.FUSED_PROVIDER)

            return when {
                hasFusedProvider -> listOf(LocationManager.FUSED_PROVIDER)
                else -> locationManager.allProviders.filter {
                    it == LocationManager.GPS_PROVIDER ||
                        it == LocationManager.NETWORK_PROVIDER ||
                        it == LocationManager.PASSIVE_PROVIDER
                }
            }
        }

    override fun shouldAskForPermission(): Boolean {
        return !isGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    override fun isLocationEnabled(): Boolean {
        return locationManager.isLocationEnabled
    }

    override fun isAnyProviderEnabled(): Boolean {
        return locationManager.isLocationEnabled &&
            locationManager.allProviders.any { locationManager.isProviderEnabled(it) }
    }

    override fun currentLocation(): Location? {
        val fix = location ?: return null
        val ageMs = TimeUnit.NANOSECONDS.toMillis(
            SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos,
        )

        if (ageMs > MAX_LOCATION_AGE) {
            location = null
        }

        return location
    }

    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    override fun startUpdates(reattach: Boolean) {
        if (isUpdating) {
            if (!reattach) return
            pauseUpdates()
        }

        isUpdating = true

        val currentProviders = providers
        val lastKnownLocations = currentProviders.map {
            locationManager.getLastKnownLocation(it)
        }

        location = getOptimalLocation(lastKnownLocations + location)

        currentProviders.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                UPDATE_INTERVAL_MS,
                0f,
                locationListener,
            )
        }
    }

    override fun pauseUpdates() {
        isUpdating = false
        locationManager.removeUpdates(locationListener)
    }

    override fun stopUpdates() {
        pauseUpdates()
        location = null
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        // Must stay above the ~10 min throttle the OS applies to coarse-only apps.
        private const val MAX_LOCATION_AGE = 15 * 60 * 1000L
        private const val UPDATE_INTERVAL_MS = 2000L
    }
}
