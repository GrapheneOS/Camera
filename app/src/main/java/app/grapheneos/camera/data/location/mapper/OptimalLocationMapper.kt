package app.grapheneos.camera.data.location.mapper

import android.location.Location
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

interface OptimalLocationMapper {
    fun map(locations: List<Location?>): Location?
}

internal class OptimalLocationMapperImpl @Inject constructor() : OptimalLocationMapper {

    override fun map(locations: List<Location?>): Location? {
        val candidates = locations.filterNotNull()
        val newest = candidates.maxByOrNull { it.elapsedRealtimeNanos } ?: return null

        return candidates
            .filter { ageDifference(newest, it) <= STALE_LOCATION_THRESHOLD }
            .minWithOrNull(MOST_ACCURATE_THEN_NEWEST)
    }

    // Order by the monotonic clock; getTime() is wall-clock and can jump.
    private fun ageDifference(newest: Location, candidate: Location): Duration {
        return (newest.elapsedRealtimeNanos - candidate.elapsedRealtimeNanos).nanoseconds
    }

    private companion object {
        private val STALE_LOCATION_THRESHOLD = 11.seconds

        // getAccuracy() is a radius in meters, so smaller is better.
        private val MOST_ACCURATE_THEN_NEWEST = compareBy<Location> { location ->
            when {
                location.hasAccuracy() -> location.accuracy
                else -> Float.MAX_VALUE
            }
        }.thenByDescending { it.elapsedRealtimeNanos }
    }
}
