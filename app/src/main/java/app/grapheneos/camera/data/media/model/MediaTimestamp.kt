package app.grapheneos.camera.data.media.model

import java.time.Instant
import java.time.LocalDateTime

sealed interface MediaTimestamp {

    data class Absolute(
        val instant: Instant,
    ) : MediaTimestamp

    data class WallClock(
        val dateTime: LocalDateTime,
    ) : MediaTimestamp
}
