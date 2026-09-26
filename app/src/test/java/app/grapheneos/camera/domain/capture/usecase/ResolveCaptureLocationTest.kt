package app.grapheneos.camera.domain.capture.usecase

import android.location.Location
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.domain.capture.model.CaptureLocation
import app.grapheneos.camera.domain.capture.usecase.ResolveCaptureLocationImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveCaptureLocationTest {

    private val locationRepository = mockk<LocationRepository>()

    private val resolveCaptureLocation = ResolveCaptureLocationImpl(locationRepository)

    @Test
    fun invoke_whenNotRequested_leavesTheRepositoryAlone() {
        runTest {
            val resolved = resolveCaptureLocation(includeLocation = false)

            assertEquals(CaptureLocation.NotRequested, resolved)
            verify(exactly = 0) { locationRepository.currentLocation() }
        }
    }

    @Test
    fun invoke_withoutAFix_reportsItUnavailable() {
        runTest {
            every { locationRepository.currentLocation() } returns null

            val resolved = resolveCaptureLocation(includeLocation = true)

            assertEquals(CaptureLocation.Unavailable, resolved)
        }
    }

    @Test
    fun invoke_withAFix_returnsIt() {
        runTest {
            val location = mockk<Location>()
            every { locationRepository.currentLocation() } returns location

            val resolved = resolveCaptureLocation(includeLocation = true)

            assertEquals(CaptureLocation.Found(location), resolved)
        }
    }
}
