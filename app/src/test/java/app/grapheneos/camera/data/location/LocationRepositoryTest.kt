package app.grapheneos.camera.data.location

import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import app.grapheneos.camera.data.location.model.LocationAvailability
import app.grapheneos.camera.data.location.repository.LocationRepositoryImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LocationRepositoryTest {

    private val locationManager = mockk<LocationManager>(relaxed = true)

    private val listener = slot<LocationListener>()

    private var locationEnabled = true

    private val repository = LocationRepositoryImpl(
        locationManager = locationManager,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Before
    fun setUp() {
        every { locationManager.hasProvider(any()) } returns false
        every { locationManager.allProviders } returns listOf(LocationManager.GPS_PROVIDER)
        every { locationManager.isLocationEnabled } answers { locationEnabled }
        every { locationManager.isProviderEnabled(any()) } answers { locationEnabled }
        every { locationManager.getLastKnownLocation(any()) } returns null
        every {
            locationManager.requestLocationUpdates(
                any<String>(),
                any<Long>(),
                any<Float>(),
                capture(listener),
                any<Looper>(),
            )
        } returns Unit
    }

    @Test
    fun updates_reportWhetherAnyProviderIsEnabled() {
        runTest(UnconfinedTestDispatcher()) {
            locationEnabled = false

            val reported = mutableListOf<LocationAvailability>()
            backgroundScope.launch { repository.updates().collect { reported += it } }

            locationEnabled = true
            listener.captured.onProviderEnabled(LocationManager.GPS_PROVIDER)

            assertEquals(
                listOf(LocationAvailability.PROVIDERS_DISABLED, LocationAvailability.AVAILABLE),
                reported,
            )
        }
    }

    @Test
    fun updates_askAgainForTheProvidersOnceOneIsEnabled() {
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch { repository.updates().collect {} }

            listener.captured.onProviderEnabled(LocationManager.GPS_PROVIDER)

            verify(exactly = 2) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    any<Long>(),
                    any<Float>(),
                    any<LocationListener>(),
                    any<Looper>(),
                )
            }
        }
    }

    @Test
    fun updates_stopWhenNoLongerCollected() {
        runTest(UnconfinedTestDispatcher()) {
            val collection = backgroundScope.launch { repository.updates().collect {} }

            collection.cancel()

            verify { locationManager.removeUpdates(listener.captured) }
        }
    }
}
