package app.grapheneos.camera.domain.capture.usecase

import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.domain.capture.model.CaptureLocation
import javax.inject.Inject

interface ResolveCaptureLocation {
    suspend operator fun invoke(includeLocation: Boolean): CaptureLocation
}

internal class ResolveCaptureLocationImpl @Inject constructor(
    private val locationRepository: LocationRepository,
) : ResolveCaptureLocation {

    override suspend fun invoke(includeLocation: Boolean): CaptureLocation {
        if (!includeLocation) {
            return CaptureLocation.NotRequested
        }

        return when (val location = locationRepository.currentLocation()) {
            null -> CaptureLocation.Unavailable
            else -> CaptureLocation.Found(location = location)
        }
    }
}
