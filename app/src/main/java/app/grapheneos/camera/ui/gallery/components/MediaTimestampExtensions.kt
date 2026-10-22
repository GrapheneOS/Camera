package app.grapheneos.camera.ui.gallery.components

import app.grapheneos.camera.data.media.model.MediaTimestamp
import java.time.LocalDateTime
import java.time.ZoneId

internal fun MediaTimestamp.localDateTime(zone: ZoneId): LocalDateTime {
    return when (this) {
        is MediaTimestamp.WallClock -> dateTime
        is MediaTimestamp.Absolute -> LocalDateTime.ofInstant(instant, zone)
    }
}
