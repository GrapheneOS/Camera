package app.grapheneos.camera.data.location.repository

import android.Manifest
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import app.grapheneos.camera.data.location.model.LocationAvailability
import app.grapheneos.camera.di.core.IoDispatcher
import app.grapheneos.camera.getOptimalLocation
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

interface LocationRepository {

    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    fun updates(): Flow<LocationAvailability>

    fun currentLocation(): Location?

    fun forgetLocation()
}

internal class LocationRepositoryImpl @Inject constructor(
    private val locationManager: LocationManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LocationRepository {

    @Volatile
    private var location: Location? = null

    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    override fun updates(): Flow<LocationAvailability> {
        return callbackFlow {
            val listener = object : LocationListener {
                override fun onLocationChanged(changedLocation: Location) {
                    keepBestLocation(listOf(changedLocation))
                }

                override fun onLocationChanged(locations: MutableList<Location>) {
                    keepBestLocation(locations)
                }

                override fun onProviderDisabled(provider: String) {
                    trySend(availability())
                }

                @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                override fun onProviderEnabled(provider: String) {
                    register(this)
                    trySend(availability())
                }
            }

            register(listener)
            trySend(availability())

            awaitClose {
                locationManager.removeUpdates(listener)
            }
        }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)
    }

    override fun currentLocation(): Location? {
        val fix = location ?: return null
        val age = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos).nanoseconds

        if (age > MAX_LOCATION_AGE) {
            location = null
        }

        return location
    }

    override fun forgetLocation() {
        location = null
    }

    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    private fun register(listener: LocationListener) {
        locationManager.removeUpdates(listener)

        val providers = trackedProviders()
        val lastKnownLocations = providers.map { provider ->
            locationManager.getLastKnownLocation(provider)
        }

        keepBestLocation(lastKnownLocations)

        providers.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                UPDATE_INTERVAL.inWholeMilliseconds,
                MIN_UPDATE_DISTANCE_METERS,
                listener,
                Looper.getMainLooper(),
            )
        }
    }

    private fun trackedProviders(): List<String> {
        return when {
            hasFusedProvider() -> listOf(LocationManager.FUSED_PROVIDER)
            else -> locationManager.allProviders.filter { it in FALLBACK_PROVIDERS }
        }
    }

    private fun hasFusedProvider(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            locationManager.hasProvider(LocationManager.FUSED_PROVIDER)
    }

    private fun keepBestLocation(candidates: List<Location?>) {
        location = getOptimalLocation(candidates + location)
    }

    private fun availability(): LocationAvailability {
        val isAnyProviderEnabled = locationManager.isLocationEnabled &&
            locationManager.allProviders.any(locationManager::isProviderEnabled)

        return when {
            isAnyProviderEnabled -> LocationAvailability.AVAILABLE
            else -> LocationAvailability.PROVIDERS_DISABLED
        }
    }

    private companion object {
        // Must stay above the ~10 min throttle the OS applies to coarse-only apps.
        private val MAX_LOCATION_AGE = 15.minutes
        private val UPDATE_INTERVAL = 2.seconds
        private const val MIN_UPDATE_DISTANCE_METERS = 0f

        private val FALLBACK_PROVIDERS = setOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    }
}
