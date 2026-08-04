package app.grapheneos.camera

import android.location.Location
import java.util.concurrent.TimeUnit

private const val STALE_LOCATION_THRESHOLD = 11 * 1000L

fun getOptimalLocation(locations: List<Location?>): Location? {
    val candidates = locations.filterNotNull()

    // Order by the monotonic clock; getTime() is wall-clock and can jump.
    val newest = candidates.maxByOrNull { it.elapsedRealtimeNanos } ?: return null

    return candidates.filter {
        val ageDifferenceMs =
            TimeUnit.NANOSECONDS.toMillis(newest.elapsedRealtimeNanos - it.elapsedRealtimeNanos)
        ageDifferenceMs <= STALE_LOCATION_THRESHOLD
    }.minWithOrNull(
        // getAccuracy() is a radius in meters, so smaller is better.
        compareBy<Location> { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
            .thenByDescending { it.elapsedRealtimeNanos }
    )
}
